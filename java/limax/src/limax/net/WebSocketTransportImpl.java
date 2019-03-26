package limax.net;

import java.net.SocketAddress;

import limax.http.WebSocketAddress;
import limax.http.WebSocketTask;
import limax.util.Trace;

class WebSocketTransportImpl extends AbstractTransport
		implements WebSocketProcessor, WebSocketTransport, SupportWebSocketTransfer {
	private final AbstractManager manager;
	private volatile WebSocketTask nettask;
	private volatile SocketAddress local;
	private volatile WebSocketAddress peer;

	public WebSocketTransportImpl(AbstractManager manager) {
		this.manager = manager;
	}

	private void dispatch(Manager _manager, WebSocketProtocol protocol) {
		protocol.setTransport(this);
		((SupportDispatch) _manager).dispatch(protocol, this);
	}

	@Override
	public void process(String in) throws Exception {
		Manager _manager = getManager();
		dispatch(_manager, ((SupportWebSocketProtocol) _manager).createWebSocketProtocol(in));
	}

	@Override
	public void process(byte[] in) throws Exception {
		Manager _manager = getManager();
		dispatch(_manager, ((SupportWebSocketProtocol) _manager).createWebSocketProtocol(in));
	}

	@Override
	public void process(int code, String reason) throws Exception {
		if (Trace.isInfoEnabled())
			Trace.info("web socket \"" + this + "\" closed code = " + code + " reason = " + reason);
	}

	@Override
	public void shutdown(Throwable closeReason) {
		synchronized (nettask) {
			setCloseReason(closeReason);
		}
		manager.removeProtocolTransport(this);
	}

	@Override
	public boolean startup(WebSocketTask nettask, SocketAddress local, WebSocketAddress peer) throws Exception {
		this.nettask = nettask;
		this.local = local;
		this.peer = peer;
		resetAlarm(0);
		manager.addProtocolTransport(this);
		return true;
	}

	@Override
	public SocketAddress getPeerAddress() {
		return peer;
	}

	@Override
	public SocketAddress getLocalAddress() {
		return local;
	}

	@Override
	public Manager getManager() {
		return manager.getOutmostWrapperManager();
	}

	@Override
	public void resetAlarm(long milliseconds) {
		nettask.resetAlarm(milliseconds);
	}

	@Override
	void close() {
		resetAlarm(0);
		nettask.sendFinal();
	}

	@Override
	public void send(String data) {
		nettask.send(data);
	}

	@Override
	public void send(byte[] data) {
		nettask.send(data);
	}

	@Override
	public String toString() {
		return getClass().getName() + " (" + local + "-" + peer + ")";
	}

}
