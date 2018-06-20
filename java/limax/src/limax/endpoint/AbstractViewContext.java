package limax.endpoint;

import limax.codec.CodecException;
import limax.endpoint.providerendpoint.SendControlToServer;
import limax.endpoint.providerendpoint.SyncViewToClients;
import limax.net.SizePolicyException;

abstract class AbstractViewContext implements ViewContext {

	abstract void onSyncViewToClients(SyncViewToClients protocol) throws Exception;

	abstract void clear() throws Exception;

	public final void sendMessage(View view, String msg)
			throws InstantiationException, ClassCastException, SizePolicyException, CodecException {
		final SendControlToServer p = new SendControlToServer();
		p.providerid = getProviderId();
		p.classindex = view.getClassIndex();
		p.instanceindex = view instanceof TemporaryView ? ((TemporaryView) view).getInstanceIndex() : 0;
		p.controlindex = -1;
		p.stringdata = msg;
		p.send(getEndpointManager().getTransport());
	}
}
