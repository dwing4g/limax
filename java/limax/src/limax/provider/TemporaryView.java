package limax.provider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import limax.codec.StringStream;
import limax.provider.ViewDataCollector.CollectorBundle;
import limax.provider.ViewDataCollector.Data;
import limax.provider.providerendpoint.SyncViewToClients;
import limax.providerendpoint.ViewMemberData;
import limax.providerendpoint.ViewVariableData;
import limax.util.Pair;

public abstract class TemporaryView extends AutoView {
	public interface CreateParameter extends View.CreateParameter {
		boolean isLoose();

		int getPartition();
	}

	public interface Membership {
		public enum AbortReason {
			SESSIONCLOSED, DUPLICATEID, INVALIDID, ADDINPROGRESS, REMOVEINPROGRESS, VIEWCLOSED, BADREASON
		}

		AbortReason add(long sessionid);

		AbortReason remove(long sessionid, byte reason);
	}

	private final static ViewVariableData NullVariableData = Data.createSpecial((byte) -1);
	private final static byte DetachReasonClose = -1;
	private final static AtomicInteger indexgenerator = new AtomicInteger();
	private final int instanceindex;
	private final Map<Long, CollectorBundle> members = new HashMap<>();
	private final Map<Long, CollectorBundle> actives = new HashMap<>();
	private final Supplier<CollectorBundle> cbcreator;
	private final Map<Short, Map<Byte, Byte>> subscribes;
	private final boolean loose;
	private final MembershipImpl membership;
	private final Map<Long, List<Pair<Byte, Data>>> pending = new HashMap<>();

	private class MembershipImpl implements Membership {
		private final Set<Long> add = new HashSet<>();
		private final Map<Long, Byte> remove = new HashMap<>();
		private final Map<Long, Partition> track = new HashMap<>();
		private final Partition[] partitions;
		private boolean blocking;
		private int current = 0;

		MembershipImpl(int p) {
			partitions = new Partition[p];
			for (int i = 0; i < p; i++)
				partitions[i] = new Partition();
		}

		private boolean isIdle() {
			for (int i = 0; i < partitions.length; i++)
				if (!partitions[i].isIdle())
					return false;
			synchronized (this) {
				if (!add.isEmpty() || !remove.isEmpty())
					return false;
			}
			return true;
		}

		@Override
		public AbortReason add(long sessionid) {
			synchronized (this) {
				if (isClosed())
					return AbortReason.VIEWCLOSED;
				if (add.contains(sessionid))
					return AbortReason.ADDINPROGRESS;
				if (remove.containsKey(sessionid))
					return AbortReason.REMOVEINPROGRESS;
				add.add(sessionid);
			}
			schedule();
			return null;
		}

		@Override
		public AbortReason remove(long sessionid, byte reason) {
			if (reason < 0)
				return AbortReason.BADREASON;
			synchronized (this) {
				if (isClosed())
					return AbortReason.VIEWCLOSED;
				if (add.contains(sessionid))
					return AbortReason.ADDINPROGRESS;
				if (remove.containsKey(sessionid))
					return AbortReason.REMOVEINPROGRESS;
				remove.put(sessionid, reason);
			}
			schedule();
			return null;
		}

		void close(long sessionid) {
			synchronized (this) {
				if (add.remove(sessionid))
					attachAbort(sessionid, AbortReason.SESSIONCLOSED);
				if (remove.remove(sessionid) != null)
					detachAbort(sessionid, AbortReason.SESSIONCLOSED);
			}
			Partition p = track.remove(sessionid);
			if (p != null)
				p.members.remove(sessionid);
		}

		void close() {
			synchronized (this) {
				add.forEach(sessionid -> attachAbort(sessionid, AbortReason.VIEWCLOSED));
				add.clear();
				remove.keySet().forEach(sessionid -> detachAbort(sessionid, AbortReason.VIEWCLOSED));
				remove.clear();
			}
			track.clear();
			for (int i = 0; i < partitions.length; i++)
				flush();
			sendCloseToClients(members.keySet());
			members.clear();
		}

		void dispatch(SyncViewToClients protocol) {
			for (Partition p : partitions)
				p.dispatch(protocol);
		}

		void loop() {
			if (++current == partitions.length)
				current = 0;
			if (!isIdle())
				schedule();
			blocking = false;
		}

		Partition current() {
			return partitions[current];
		}

		void flush() {
			if (isScriptEnabled()) {
				StringBuilder varstring = cb.string(true).collect(StringBuilder::new, StringBuilder::append,
						StringBuilder::append);
				for (Partition p : partitions)
					p.dispatchVariableString(varstring);
				current().dispatchVariableString(
						cb.string(false).collect(StringBuilder::new, StringBuilder::append, StringBuilder::append));
				actives.forEach((k, c) -> {
					String q = StringStream.create().marshal(k).toString();
					StringBuilder mbstring = c.string(true).collect(StringBuilder::new, (s, v) -> s.append(q).append(v),
							StringBuilder::append);
					for (Partition p : partitions)
						p.dispatchMemberString(mbstring);
					current().dispatchMemberString(c.string(false).collect(StringBuilder::new,
							(s, v) -> s.append(q).append(v), StringBuilder::append));
				});
			}
			List<ViewVariableData> varbinary = cb.binary(true).collect(Collectors.toList());
			for (Partition p : partitions)
				p.dispatchVariableBinary(varbinary);
			current().dispatchVariableBinary(cb.binary(false).collect(Collectors.toList()));
			cb.reset();
			actives.forEach((k, c) -> {
				List<ViewMemberData> mbbinary = c.binary(true).map(v -> new ViewMemberData(k, v))
						.collect(Collectors.toList());
				for (Partition p : partitions)
					p.dispatchMemberBinary(mbbinary);
				current().dispatchMemberBinary(
						c.binary(false).map(v -> new ViewMemberData(k, v)).collect(Collectors.toList()));
				c.reset();
			});
			actives.clear();
			current().flush();
		}
	}

	private class Partition {
		private final Set<Long> members = new HashSet<>();
		private final List<SyncViewToClients> queue = new ArrayList<>();
		private ArrayList<ViewVariableData> varbinary = new ArrayList<>();
		private StringBuilder varstring = new StringBuilder();
		private ArrayList<ViewMemberData> mbbinary = new ArrayList<>();
		private StringBuilder mbstring = new StringBuilder();
		private final Map<Long, Map<Byte, Data>> adding = new HashMap<>();
		private int affect;

		boolean isIdle() {
			return queue.isEmpty() && varbinary.isEmpty() && mbbinary.isEmpty();
		}

		void dispatchVariableBinary(List<ViewVariableData> v) {
			varbinary.addAll(v);
		}

		void dispatchMemberBinary(List<ViewMemberData> v) {
			mbbinary.addAll(v);
		}

		void dispatchVariableString(StringBuilder v) {
			varstring.append(v);
		}

		void dispatchMemberString(StringBuilder v) {
			mbstring.append(v);
		}

		private void assemble() {
			if (!varbinary.isEmpty() || !mbbinary.isEmpty()) {
				SyncViewToClients p = protocol(SyncViewToClients.DT_TEMPORARY_DATA);
				p.vardatas = varbinary;
				p.members = mbbinary;
				varbinary = new ArrayList<>();
				mbbinary = new ArrayList<>();
				if (isScriptEnabled()) {
					p.stringdata = prepareStringHeader(p).append(varstring.toString()).append(":P")
							.append(mbstring.toString()).toString(":");
					varstring = new StringBuilder();
					mbstring = new StringBuilder();
				}
				queue.add(p);
			}
		}

		void dispatch(SyncViewToClients protocol) {
			assemble();
			queue.add(protocol);
		}

		void flush() {
			assemble();
			if (!queue.isEmpty()) {
				for (SyncViewToClients p : queue)
					if (!members.isEmpty()) {
						p.sessionids.clear();
						p.sessionids.addAll(members);
						syncViewToClients(p);
					}
				queue.clear();
			}
		}

		boolean collect() {
			synchronized (membership) {
				if (!membership.remove.isEmpty()) {
					Set<Long> removeset = new HashSet<>(members);
					removeset.retainAll(membership.remove.keySet());
					removeset.forEach(sessionid -> remove1(sessionid, membership.remove.remove(sessionid)));
					removeset = new HashSet<>(membership.remove.keySet());
					removeset.removeAll(TemporaryView.this.members.keySet());
					if (!removeset.isEmpty()) {
						membership.remove.keySet().removeAll(removeset);
						removeset.forEach(sessionid -> detachAbort(sessionid, Membership.AbortReason.INVALIDID));
					}
				}
				if (!membership.add.isEmpty()
						&& members.size() * membership.partitions.length <= TemporaryView.this.members.size()) {
					affect = (int) membership.add.stream().filter(sessionid -> {
						if (membership.track.putIfAbsent(sessionid, this) == null)
							return true;
						else {
							attachAbort(sessionid, Membership.AbortReason.DUPLICATEID);
							return false;
						}
					}).peek(sessionid -> schedule(sessionid, () -> add1(sessionid))).count();
					membership.add.clear();
				}
			}
			return membership.blocking = affect > 0;
		}

		private void remove1(long sessionid, byte reason) {
			if (isClosed())
				detachAbort(sessionid, Membership.AbortReason.VIEWCLOSED);
			else if (membership.track.remove(sessionid) == null)
				detachAbort(sessionid, Membership.AbortReason.SESSIONCLOSED);
			else {
				members.remove(sessionid);
				TemporaryView.this.members.remove(sessionid);
				if (!loose)
					membership.dispatch(createDetachNotify(sessionid, reason));
				sendCloseToClients(Arrays.asList(sessionid));
				unsubscribe(sessionid);
				try {
					onDetached(sessionid, reason);
				} catch (Throwable t) {
				}
			}
		}

		private void add1(long sessionid) {
			ViewSession vs = getViewContext().getViewSession(sessionid);
			boolean miss = vs == null || vs.isClosed();
			Map<Byte, Data> init = (miss || loose) ? null
					: subscribes.entrySet().stream()
							.flatMap(e -> vs.findSessionView(e.getKey()).subscribe(TemporaryView.this, e.getValue()))
							.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
			schedule(() -> add2(sessionid, init, miss));
		}

		private void add2(long sessionid, Map<Byte, Data> init, boolean miss) {
			if (miss) {
				membership.track.remove(sessionid);
				attachAbort(sessionid, Membership.AbortReason.SESSIONCLOSED);
			} else {
				if (!loose)
					pending.put(sessionid, new ArrayList<>());
				adding.put(sessionid, init);
			}
			if (--affect == 0)
				gather();
		}

		private void gather() {
			if (isClosed()) {
				adding.keySet().forEach(sessionid -> attachAbort(sessionid, Membership.AbortReason.VIEWCLOSED));
				adding.clear();
				return;
			}
			for (Iterator<Map.Entry<Long, Map<Byte, Data>>> it = adding.entrySet().iterator(); it.hasNext();) {
				long sessionid = it.next().getKey();
				if (!membership.track.containsKey(sessionid)) {
					if (!loose)
						pending.remove(sessionid);
					attachAbort(sessionid, Membership.AbortReason.SESSIONCLOSED);
					it.remove();
				}
			}
			if (!adding.isEmpty()) {
				if (loose)
					adding.keySet().forEach(sessionid -> add4(sessionid, null, null));
				else
					add3(adding);
				adding.clear();
			}
			membership.flush();
			membership.loop();
		}

		private void add3(Map<Long, Map<Byte, Data>> map) {
			map.forEach((sessionid, init) -> membership.dispatch(createAttachNotify(sessionid, init)));
			membership.flush();
			map.forEach((sessionid, init) -> {
				CollectorBundle cb = cbcreator.get();
				init.forEach((k, v) -> cb.set(k, v));
				add4(sessionid, cb, map.keySet());
				if (pending.containsKey(sessionid))
					actives.put(sessionid, cb);
			});
			if (map.size() > 1)
				map.forEach((sessionid, init) -> {
					SyncViewToClients p = createAttachNotify(sessionid, init);
					p.sessionids.addAll(map.keySet());
					p.sessionids.remove(sessionid);
					syncViewToClients(p);
				});
			pending.forEach((sessionid, r) -> r.forEach(e -> _update(sessionid, e.getKey(), e.getValue())));
			pending.clear();
		}

		private void add4(long sessionid, CollectorBundle cb, Set<Long> exception) {
			members.add(sessionid);
			TemporaryView.this.members.put(sessionid, cb);
			sendInitViewToClient(sessionid, exception);
			subscribe(sessionid);
			try {
				onAttached(sessionid);
			} catch (Throwable t) {
				schedule(() -> close0(sessionid, true));
			}
		}
	}

	protected TemporaryView(CreateParameter param, String[] prefix, byte[][] collectors,
			Map<Short, Map<Byte, Byte>> subscribes, byte[][] subscribe_collectors) {
		super(param, prefix, collectors, param.getPartition());
		this.instanceindex = indexgenerator.incrementAndGet();
		this.loose = subscribes.isEmpty() ? param.isLoose() : false;
		this.membership = new MembershipImpl(param.getPartition());
		this.subscribes = subscribes;
		this.cbcreator = () -> vdc.createCollectorBundle(subscribe_collectors, param.getPartition());
	}

	@Override
	public void schedule(Runnable task) {
		schedule(this, task);
	}

	@Override
	public final int getInstanceIndex() {
		return instanceindex;
	}

	public final Membership getMembership() {
		return membership;
	}

	protected abstract void onAttachAbort(long sessionid, Membership.AbortReason reason);

	protected abstract void onDetachAbort(long sessionid, Membership.AbortReason reason);

	protected abstract void onAttached(long sessionid);

	protected abstract void onDetached(long sessionid, byte reason);

	@Override
	void onUpdate(byte varindex, Data data) {
		schedule();
	}

	private void _update(long sessionid, byte varindex, Data data) {
		CollectorBundle cb = members.get(sessionid);
		if (cb != null) {
			cb.add(varindex, data);
			actives.put(sessionid, cb);
			schedule();
		} else {
			List<Pair<Byte, Data>> list = pending.get(sessionid);
			if (list != null)
				list.add(new Pair<>(varindex, data));
		}
	}

	void onUpdate(long sessionid, byte varindex, Data data) {
		schedule(() -> _update(sessionid, varindex, data));
	}

	@Override
	void flush() {
		if (isClosed() || membership.blocking || membership.current().collect())
			return;
		membership.flush();
		membership.loop();
	}

	private void subscribe(long sessionid) {
		schedule(sessionid, () -> {
			ViewSession vs = getViewContext().getViewSession(sessionid);
			if (vs != null && !vs.isClosed())
				vs.add(this);
		});
	}

	private void unsubscribe(long sessionid) {
		schedule(sessionid, () -> {
			ViewSession vs = getViewContext().getViewSession(sessionid);
			if (vs != null && !vs.isClosed()) {
				vs.remove(this);
				subscribes.keySet().stream().map(k -> vs.findSessionView(k)).forEach(v -> v.unsubscribe(this));
			}
		});
	}

	private void attachAbort(long sessionid, Membership.AbortReason reason) {
		try {
			onAttachAbort(sessionid, reason);
		} catch (Throwable t) {
		}
	}

	private void detachAbort(long sessionid, Membership.AbortReason reason) {
		try {
			onDetachAbort(sessionid, reason);
		} catch (Throwable t) {
		}
	}

	private void close0(long sessionid, boolean abort) {
		if (isClosed())
			return;
		membership.close(sessionid);
		if (loose) {
			if (!members.containsKey(sessionid))
				return;
			members.remove(sessionid);
		} else {
			if (members.remove(sessionid) == null)
				return;
			actives.remove(sessionid);
			membership.dispatch(createDetachNotify(sessionid, DetachReasonClose));
			schedule();
		}
		unsubscribe(sessionid);
		if (!abort)
			onDetached(sessionid, DetachReasonClose);
	}

	void close(long sessionid) {
		schedule(() -> close0(sessionid, false));
	}

	void close(long sessionid, Runnable done) {
		schedule(() -> {
			try {
				close0(sessionid, false);
			} finally {
				done.run();
			}
		});
	}

	@Override
	void close() {
		schedule(() -> {
			super.close(() -> {
				members.keySet().forEach(sessionid -> {
					try {
						unsubscribe(sessionid);
						onDetached(sessionid, DetachReasonClose);
					} catch (Throwable e) {
					}
				});
				membership.close();
			});
		});
	}

	private void sendInitViewToClient(long sessionid, Set<Long> exception) {
		SyncViewToClients p = protocol(SyncViewToClients.DT_TEMPORARY_INIT_DATA);
		p.sessionids.add(sessionid);
		cb.snapshot().flatMap(e -> vdc.binary(e)).collect(() -> p.vardatas, List::add, List::addAll);
		if (!loose)
			members.entrySet().stream().filter(e -> !exception.contains(e.getKey()) || e.getKey() == sessionid)
					.forEach(e -> {
						int len = p.members.size();
						e.getValue().snapshot().flatMap(s -> vdc.binary(s)).map(v -> new ViewMemberData(e.getKey(), v))
								.collect(() -> p.members, List::add, List::addAll);
						if (len == p.members.size())
							p.members.add(new ViewMemberData(e.getKey(), NullVariableData));
					});
		if (isScriptEnabled()) {
			StringStream m = prepareStringHeader(p)
					.append(cb.snapshot().flatMap(s -> vdc.string(s)).collect(Collectors.joining())).append(":P");
			if (!loose)
				members.entrySet().stream().filter(e -> !exception.contains(e.getKey()) || e.getKey() == sessionid)
						.forEach(e -> {
							int len = m.length();
							e.getValue().snapshot().flatMap(s -> vdc.string(s))
									.forEach(v -> m.marshal(e.getKey()).append(v));
							if (len == m.length())
								m.marshal(e.getKey()).append('U');
						});
			p.stringdata = m.toString(":");
		}
		syncViewToClients(p);
	}

	private SyncViewToClients createAttachNotify(long sessionid, Map<Byte, Data> init) {
		SyncViewToClients p = protocol(SyncViewToClients.DT_TEMPORARY_ATTACH);
		init.entrySet().stream().flatMap(e -> vdc.binary(e).map(v -> new ViewMemberData(sessionid, v)))
				.collect(() -> p.members, List::add, List::addAll);
		if (p.members.isEmpty())
			p.members.add(new ViewMemberData(sessionid, NullVariableData));
		if (isScriptEnabled()) {
			StringStream m = prepareStringHeader(p).append(":P");
			int len = m.length();
			init.entrySet().stream().forEach(e -> vdc.string(e).forEach(v -> m.marshal(sessionid).append(v)));
			if (len == m.length())
				m.marshal(sessionid).append('U');
			p.stringdata = m.toString(":");
		}
		return p;
	}

	private void sendCloseToClients(Collection<Long> sessionids) {
		if (sessionids.isEmpty())
			return;
		SyncViewToClients p = protocol(SyncViewToClients.DT_TEMPORARY_CLOSE);
		p.sessionids.addAll(sessionids);
		if (isScriptEnabled())
			p.stringdata = prepareStringHeader(p).append(":M:").toString();
		syncViewToClients(p);
	}

	private SyncViewToClients createDetachNotify(long sessionid, byte reason) {
		SyncViewToClients p = protocol(SyncViewToClients.DT_TEMPORARY_DETACH);
		p.members.add(new ViewMemberData(sessionid, Data.createSpecial(reason)));
		if (isScriptEnabled())
			p.stringdata = prepareStringHeader(p).append(":P").marshal(sessionid).marshal(reason).toString(":");
		return p;
	}

	@Override
	public String toString() {
		return "[class = " + getClass().getName() + " ProviderId = " + getProviderId() + " classindex = "
				+ getClassIndex() + " instanceName = " + getInstanceIndex() + "]";
	}
}
