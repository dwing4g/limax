package limax.net;

public interface SupportWebSocketProtocol {
	WebSocketProtocol createWebSocketProtocol(String text);

	WebSocketProtocol createWebSocketProtocol(byte[] binary);
}
