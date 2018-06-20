package limax.endpoint;

import java.util.Set;

import limax.codec.Base64Encode;
import limax.codec.Octets;
import limax.codec.StringStream;
import limax.endpoint.providerendpoint.SendControlToServer;
import limax.endpoint.providerendpoint.SyncViewToClients;
import limax.endpoint.script.ScriptEngineHandle;
import limax.endpoint.script.ScriptSender;

class ScriptExchange implements ScriptSender {
	private final EndpointManagerImpl netmanager;
	private final ScriptEngineHandle handle;
	private final Set<Integer> providers;
	private Object closeReason;

	ScriptExchange(EndpointManagerImpl netmanager, ScriptEngineHandle handle) {
		this.netmanager = netmanager;
		this.handle = handle;
		this.providers = handle.getProviders();
	}

	private void process(int t, Object p) throws Exception {
		if (handle.action(t, p) == 3)
			netmanager.close();
	}

	synchronized void onLoad(String welcome) throws Exception {
		handle.registerScriptSender(this);
		process(1, welcome);
	}

	synchronized void onSyncViewToClients(SyncViewToClients protocol) throws Exception {
		if (!protocol.stringdata.isEmpty() && providers.contains(protocol.providerid))
			process(1, protocol.stringdata);
	}

	synchronized void onTunnel(int providerid, int label, Octets data) throws Exception {
		process(1, StringStream.create().marshal(new String(Base64Encode.transform(data.getBytes())))
				.marshal(providerid).marshal(label).toString());
	}

	synchronized void onClose(Object closeReason) throws Exception {
		this.closeReason = closeReason;
	}

	synchronized void onUnload() {
		try {
			handle.action(2, closeReason);
		} catch (Exception e) {
		}
	}

	@Override
	public Throwable send(String s) {
		final SendControlToServer protocol = new SendControlToServer();
		protocol.providerid = -1;
		protocol.stringdata = s;
		try {
			protocol.send(netmanager.getTransport());
		} catch (Exception e) {
			return e;
		}
		return null;
	}
}
