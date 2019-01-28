package limax.net;

import javax.net.ssl.SSLSession;

public interface WebSocketTask {
	void send(String data);

	void send(byte[] data);

	void sendFinal(long timeout);

	void sendFinal();

	void resetAlarm(long millisecond);

	void enable();

	void disable();

	SSLSession getSSLSession();
}
