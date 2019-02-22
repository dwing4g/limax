package limax.http;

import java.net.InetSocketAddress;

public interface WebSocketExchange extends WebSocketTask {
	InetSocketAddress getLocalAddress();

	WebSocketAddress getPeerAddress();

	void setSessionObject(Object obj);

	Object getSessionObject();

	long ping();
}
