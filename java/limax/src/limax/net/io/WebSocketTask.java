package limax.net.io;

public interface WebSocketTask {
	void send(String data);

	void send(byte[] data);

	void sendFinal();

	void resetAlarm(long millisecond);
}
