package limax.http;

import javax.net.ssl.SSLSession;

public interface WebSocketTask {
	void send(String text);

	void send(byte[] binary);

	void sendFinal(long timeout);

	void sendFinal();

	void resetAlarm(long milliseconds);

	void enable();

	void disable();

	SSLSession getSSLSession();
}
