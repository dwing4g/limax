package limax.http;

import java.net.SocketTimeoutException;

import javax.net.ssl.SSLSession;

class WebSocket11Exchange extends AbstractWebSocketExchange {

	WebSocket11Exchange(WebSocketHandler handler, AbstractHttpExchange exchange) {
		super(handler, exchange);
	}

	@Override
	void executeAlarmTask() {
		processor.nettask().cancel(new SocketTimeoutException("http/1.1 websocket closed by alarm"));
	}

	@Override
	public void enable() {
		processor.nettask().enable();
	}

	@Override
	public void disable() {
		processor.nettask().disable();
	}

	@Override
	public SSLSession getSSLSession() {
		return processor.nettask().getSSLSession();
	}
}
