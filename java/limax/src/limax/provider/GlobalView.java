package limax.provider;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import limax.provider.ViewDataCollector.Data;
import limax.provider.providerendpoint.SyncViewToClients;

public abstract class GlobalView extends View {
	public interface CreateParameter extends View.CreateParameter {
	}

	private final static Runnable donothing = () -> {
	};
	private final Map<String, Byte> name2index = new LinkedHashMap<>();
	private final Map<Byte, String> index2name = new HashMap<>();

	protected GlobalView(CreateParameter param, String[] prefix, byte[][] collectors, String[] varnames) {
		super(param, prefix, collectors, 1);
		for (int i = 0; i < varnames.length; i++) {
			name2index.put(varnames[i], (byte) i);
			index2name.put((byte) i, varnames[i]);
		}
	}

	protected void onUpdate(String varname) {
	}

	@Override
	public void schedule(Runnable task) {
		schedule(this, task);
	}

	public final void syncToClient(Collection<Long> sessionids, String varname) {
		syncToClient(sessionids, varname, donothing);
	}

	public final void syncToClient(Collection<Long> sessionids) {
		syncToClient(sessionids, donothing);
	}

	public final void syncToClient(long sessionid, String varname) {
		syncToClient(Arrays.asList(sessionid), varname);
	}

	public final void syncToClient(long sessionid) {
		syncToClient(Arrays.asList(sessionid));
	}

	public final void syncToClient(Collection<Long> sessionids, String varname, Runnable done) {
		syncToClient(sessionids, Arrays.asList(name2index.get(varname)), done);
	}

	public final void syncToClient(Collection<Long> sessionids, Runnable done) {
		syncToClient(sessionids, name2index.values(), done);
	}

	public final void syncToClient(long sessionid, String varname, Runnable done) {
		syncToClient(Arrays.asList(sessionid), varname);
	}

	public final void syncToClient(long sessionid, Runnable done) {
		syncToClient(Arrays.asList(sessionid));
	}

	private void syncToClient(Collection<Long> sessionids, Collection<Byte> varindexes, Runnable done) {
		schedule(() -> {
			try {
				if (isClosed())
					return;
				SyncViewToClients p = protocol(SyncViewToClients.DT_VIEW_DATA);
				p.sessionids.addAll(sessionids);
				varindexes.stream().flatMap(e -> vdc.binary(e, cb.get(e))).collect(() -> p.vardatas, List::add,
						List::addAll);
				if (!p.vardatas.isEmpty()) {
					if (isScriptEnabled())
						p.stringdata = prepareStringHeader(p).append(varindexes.stream()
								.flatMap(e -> vdc.string(e, cb.get(e))).collect(Collectors.joining())).toString(":P:");
					syncViewToClients(p);
				}
			} catch (Throwable t) {
				done.run();
			}
		});
	}

	@Override
	void onUpdate(byte varindex, Data data) {
		onUpdate(index2name.get(varindex));
	}

	@Override
	void close() {
		schedule(() -> super.close());
	}
}
