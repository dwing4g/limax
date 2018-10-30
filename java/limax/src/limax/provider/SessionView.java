package limax.provider;

import java.net.URI;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import limax.codec.Octets;
import limax.provider.ViewDataCollector.Data;
import limax.provider.providerendpoint.SyncViewToClients;
import limax.util.Pair;

public abstract class SessionView extends AutoView {
	public interface CreateParameter extends View.CreateParameter {
		long getSessionId();
	}

	private final long sessionid;
	private Map<TemporaryView, Map<Byte, Byte>> subscribes;

	protected SessionView(CreateParameter param, String[] prefix, byte[][] collectors) {
		super(param, prefix, collectors, 1);
		this.sessionid = param.getSessionId();
	}

	public final long getSessionId() {
		return sessionid;
	}

	@Override
	public void schedule(Runnable task) {
		schedule(sessionid, task);
	}

	Stream<Pair<Byte, Data>> subscribe(TemporaryView tview, Map<Byte, Byte> varindexes) {
		if (subscribes == null)
			subscribes = new IdentityHashMap<>();
		subscribes.put(tview, varindexes);
		return varindexes.entrySet().stream().map(e -> new Pair<>(e.getValue(), cb.get(e.getKey())));
	}

	void unsubscribe(TemporaryView tview) {
		if (subscribes == null)
			return;
		subscribes.remove(tview);
		if (subscribes.isEmpty())
			subscribes = null;
	}

	@Override
	void onUpdate(byte varindex, Data data) {
		if (subscribes != null)
			subscribes.entrySet().stream().filter(e -> e.getValue().containsKey(varindex))
					.forEach(e -> e.getKey().onUpdate(sessionid, e.getValue().get(varindex), data));
		schedule();
	}

	@Override
	void flush() {
		if (isClosed())
			return;
		SyncViewToClients p = protocol(SyncViewToClients.DT_VIEW_DATA);
		p.sessionids.add(sessionid);
		cb.binary().collect(() -> p.vardatas, List::add, List::addAll);
		if (!p.vardatas.isEmpty()) {
			if (isScriptEnabled())
				p.stringdata = prepareStringHeader(p).append(cb.string().collect(Collectors.joining())).toString(":P:");
			syncViewToClients(p);
		}
		cb.reset();
	}

	public final void force(Runnable done) {
		schedule(() -> {
			flush();
			if (done != null)
				done.run();
		});
	}

	@Override
	void close() {
		schedule(() -> super.close());
	}

	public final void tunnel(URI group, int label, Octets data) throws TunnelException {
		tunnel(sessionid, group, label, data);
	}

	public final void tunnel(int label, Octets data) throws TunnelException {
		tunnel(sessionid, null, label, data);
	}
}
