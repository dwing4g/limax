package limax.globalid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import limax.globalid.providerglobalid.KeepAlive;
import limax.net.Config;
import limax.net.Engine;
import limax.net.Manager;
import limax.net.ServerListener;
import limax.net.State;
import limax.net.Transport;
import limax.providerglobalid.GroupName;
import limax.util.Pair;
import limax.util.Trace;
import limax.xmlconfig.Service;
import limax.zdb.Procedure;
import limax.zdb.XDeadlock;

public class Main {
	public final static class GlobalIdServer implements ServerListener {
		private Future<?> keepAliveFuture;

		static private GlobalIdServer instance = new GlobalIdServer();

		private GlobalIdServer() {
		}

		public static GlobalIdServer getInstance() {
			return instance;
		}

		@Override
		public void onTransportRemoved(Transport transport) {
			Main.unlock(transport);
		}

		@Override
		public void onTransportAdded(Transport transport) {
			synchronized (Main.transporthold) {
				Main.transporthold.put(transport, new HashSet<Integer>());
			}
		}

		public State getDefaultState() {
			return limax.globalid.states.GlobalIdServer.getDefaultState();
		}

		@Override
		public void onManagerInitialized(Manager manager, Config config) {
			long keepAliveTimeout = Engine.getIntranetKeepAliveTimeout();
			if (keepAliveTimeout > 0)
				keepAliveFuture = Engine.getProtocolScheduler().scheduleWithFixedDelay(() -> {
					Collection<Transport> transports;
					synchronized (Main.transporthold) {
						transports = new ArrayList<Transport>(Main.transporthold.keySet());
					}
					KeepAlive p = new KeepAlive(keepAliveTimeout);
					try {
						for (Transport transport : transports)
							p.send(transport);
					} catch (Exception e) {
					}
				} , 0, keepAliveTimeout / 2, TimeUnit.MILLISECONDS);
			Service.addRunAfterEngineStartTask(() -> {
				try {
					Procedure.call(() -> {
						if (table.Maxgroupid.insert(0, maxgroupid) == null)
							maxgroupid = table.Maxgroupid.select(0);
						table.Namegroups.get().walk((k, v) -> {
							groupids.put(k, v);
							return true;
						});
						return true;
					});
				} catch (Exception e) {
					Trace.fatal("get maxgroupid fatal.", e);
					System.exit(0);
				}
			});
		}

		@Override
		public void onManagerUninitialized(Manager manager) {
			if (keepAliveFuture != null)
				keepAliveFuture.cancel(true);
		}
	}

	static class IgnoreOperationException extends Exception {
		private static final long serialVersionUID = 5091994190547741061L;
	}

	private static class GroupNameOperation {
		private enum Operation {
			NONE, CREATE, DELETE,
		}

		private final GroupName gn;
		private volatile Operation op = Operation.NONE;

		public GroupNameOperation(GroupName gn) {
			this.gn = gn;
		}

		void setCreate() {
			op = Operation.CREATE;
		}

		void setDelete() {
			op = Operation.DELETE;
		}

		boolean isNone() {
			return op == Operation.NONE;
		}

		boolean isCreate() {
			return op == Operation.CREATE;
		}

		boolean isDelete() {
			return op == Operation.DELETE;
		}

		GroupName getGroupName() {
			return gn;
		}
	}

	static short maxgroupid = 0;
	private static AtomicInteger keyGenerator = new AtomicInteger(0);
	private static AtomicInteger tidGenerator = new AtomicInteger(1);
	private static Map<Long, GroupNameOperation> serials = new ConcurrentHashMap<>();
	private static Map<Integer, Queue<Long>> lockhold = new ConcurrentHashMap<>();
	private static Map<GroupName, Integer> owner = new ConcurrentHashMap<>();
	private static final Map<GroupName, Queue<Integer>> waiters = new ConcurrentHashMap<>();
	private static final Map<Transport, Set<Integer>> transporthold = new ConcurrentHashMap<>();

	static long lock(GroupName gn, long mixed, Transport transport) throws IgnoreOperationException {
		int tid;
		if ((mixed & 1) == 0) {
			tid = tidGenerator.getAndAdd(2);
			lockhold.put(tid, new ConcurrentLinkedQueue<>());
			synchronized (transporthold) {
				Set<Integer> tids = transporthold.get(transport);
				if (tids == null)
					throw new IgnoreOperationException();
				tids.add(tid);
			}
		} else {
			tid = (int) mixed;
		}
		if (owner.putIfAbsent(gn, tid) != null) {
			synchronized (waiters) {
				for (Collection<GroupName> hold = lockhold.get(tid).stream().map(i -> serials.get(i).getGroupName())
						.collect(Collectors.toList()); !hold.isEmpty();hold = hold.stream()
								.filter(i -> waiters.containsKey(i)).flatMap(i -> waiters.get(i).stream())
								.filter(i -> lockhold.containsKey(i)).flatMap(i -> lockhold.get(i).stream())
								.map(i -> serials.get(i).getGroupName()).collect(() -> new ArrayList<>(), (c, e) -> {
									if (e.equals(gn))
										throw new XDeadlock();
									c.add(e);
								} , List::addAll))
					;
				Queue<Integer> q = waiters.get(gn);
				if (q == null)
					waiters.put(gn, q = new ArrayDeque<>());
				q.add(tid);
				do {
					try {
						waiters.wait();
					} catch (InterruptedException e) {
					}
					if (!q.contains(tid)) {
						if (q.isEmpty())
							waiters.remove(gn);
						throw new IgnoreOperationException();
					}
				} while (q.peek() != tid || owner.putIfAbsent(gn, tid) != null);
				q.remove();
				if (q.isEmpty())
					waiters.remove(gn);
			}
		}
		synchronized (transporthold) {
			if (!transporthold.containsKey(transport)) {
				owner.remove(gn);
				throw new IgnoreOperationException();
			}
			long serial = ((long) keyGenerator.getAndIncrement() << 32) | tid;
			lockhold.get(tid).add(serial);
			serials.put(serial, new GroupNameOperation(gn));
			return serial;
		}
	}

	static void unlock(Transport transport) {
		Set<Integer> tids;
		synchronized (transporthold) {
			tids = transporthold.remove(transport);
		}
		tids.stream().flatMap(i -> lockhold.remove(i).stream())
				.forEach(s -> owner.remove(serials.remove(s).getGroupName()));
		synchronized (waiters) {
			waiters.values().forEach(i -> i.removeAll(tids));
			waiters.notifyAll();
		}
	}

	static void unlock(Transport transport, int tid) {
		boolean need = false;
		synchronized (transporthold) {
			Set<Integer> tids = transporthold.get(transport);
			if (tids != null) {
				tids.remove(tid);
				need = true;
			}
		}
		if (need) {
			boolean b[] = new boolean[] { false };
			lockhold.remove(tid).forEach(s -> {
				GroupName gn = serials.remove(s).getGroupName();
				owner.remove(gn);
				if (!b[0] && waiters.containsKey(gn))
					b[0] = true;
			});
			if (b[0])
				synchronized (waiters) {
					waiters.notifyAll();
				}
		}
	}

	static Stream<Pair<cbean.NameKey, Boolean>> endorse(int tid) {
		Queue<Long> tid2serials = lockhold.get(tid);
		return tid2serials == null ? Stream.empty()
				: tid2serials.stream().map(i -> serials.get(i)).filter(i -> !i.isNone())
						.map(i -> new Pair<>(
								new cbean.NameKey(groupids.get(i.getGroupName().grp), i.getGroupName().name),
								i.isCreate()))
						.sorted((a, b) -> a.getKey().compareTo(b.getKey()));
	}

	static boolean isCreate(long serial) {
		return serials.get(serial).isCreate();
	}

	static boolean isDelete(long serial) {
		return serials.get(serial).isDelete();
	}

	static void setCreate(long serial) {
		serials.get(serial).setCreate();
	}

	static void setDelete(long serial) {
		serials.get(serial).setDelete();
	}

	static GroupName getGroupName(long serial) {
		return serials.get(serial).getGroupName();
	}

	static Map<String, Short> groupids = new ConcurrentHashMap<>();

	public static void main(String args[]) throws Exception {
		Service.run(args.length > 0 ? args[0] : "service-globalid.xml");
	}
}
