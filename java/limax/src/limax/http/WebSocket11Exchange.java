package limax.http;

import java.net.InetSocketAddress;
import java.util.function.Function;

import javax.net.ssl.SSLSession;

import limax.net.io.NetTask;

class WebSocket11Exchange extends AbstractWebSocketExchange {
	private final NetTask nettask;

	WebSocket11Exchange(ApplicationExecutor executor, NetTask nettask, WebSocketHandler handler,
			Function<Runnable, CustomSender> senderCreator, InetSocketAddress local, WebSocketAddress peer,
			int maxMessageSize, long defaultFinalTimeout) {
		super(executor, handler, senderCreator, local, peer, maxMessageSize, defaultFinalTimeout);
		this.nettask = nettask;
	}

	@Override
	public void enable() {
		nettask.enable();
	}

	@Override
	public void disable() {
		nettask.disable();
	}

	@Override
	public void resetAlarm(long milliseconds) {
		nettask.resetAlarm(milliseconds);
	}

	@Override
	public SSLSession getSSLSession() {
		return nettask.getSSLSession();
	}
}
