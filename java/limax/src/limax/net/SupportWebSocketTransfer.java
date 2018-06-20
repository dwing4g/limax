package limax.net;

public interface SupportWebSocketTransfer {
	void send(String data);

	void send(byte[] data);
}
