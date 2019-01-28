package limax.util.transpond;

import java.net.SocketAddress;

import limax.net.io.NetModel;

class ProxyServerSession extends ProxySession {
	private Transpond transpond;

	ProxyServerSession(Transpond transpond) {
		super(transpond.buffersize);
		this.transpond = transpond;
	}

	@Override
	public boolean startup(FlowControlTask task, SocketAddress local, SocketAddress peer) throws Exception {
		ProxyClientSession pcs = new ProxyClientSession(this, transpond);
		NetStation fcn = new NetStation(pcs, transpond.buffersize);
		pcs.setAssocNetTask(fcn);
		NetModel.addClient(transpond.peer, NetModel.createClientTask(transpond.buffersize, transpond.buffersize, null,
				fcn, transpond.asynchronous));
		return false;
	}

}
