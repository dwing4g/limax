package limax.util.transpond;

import java.net.SocketAddress;

class ProxyClientSession extends ProxySession {

	ProxyClientSession(ProxyServerSession pss, Transpond transpond) {
		super(transpond.buffersize);
		setProxySession(pss);
		pss.setProxySession(this);
	}

	@Override
	public boolean startup(FlowControlTask task, SocketAddress local, SocketAddress peer) throws Exception {
		tosession.nettask.enableReceive();
		return true;
	}

}
