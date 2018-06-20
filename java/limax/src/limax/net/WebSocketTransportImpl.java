package limax.net;

import java.net.SocketAddress;

import limax.net.io.WebSocketAddress;
import limax.net.io.WebSocketProcessor;
import limax.net.io.WebSocketTask;
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

	private void dispatch(WebSocketProtocol protocol) {
		protocol.setTransport(this);
		manager.dispatch(protocol, this);
	}

	@Override
	public void process(String in) throws Exception {
		dispatch(((SupportWebSocketProtocol) getManager()).createWebSocketProtocol(in));
	}

	@Override
	public void process(byte[] in) throws Exception {
		dispatch(((SupportWebSocketProtocol) getManager()).createWebSocketProtocol(in));
	}

	@Override
	public void process(int code, String reason) throws Exception {
		if (Trace.isInfoEnabled())
			Trace.info("web socket \"" + this + "\" closed code = " + code + " reason = " + reason);
	}

	@Override
	public void shutdown(boolean eventually, Throwable closeReason) {
		synchronized (nettask) {
			if (closeReason != null)
				setCloseReason(closeReason);
		}
		manager.removeProtocolTransport(this);
	}

	@Override
	public void setup(WebSocketTask nettask) {
		this.nettask = nettask;
	}

	@Override
	public boolean setup(SocketAddress local, WebSocketAddress peer) throws Exception {
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
	public void resetAlarm(long millisecond) {
		nettask.resetAlarm(millisecond);
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
