package limax.http;

import limax.util.Pair;

public class WebSocketEvent {
	public enum Type {
		OPEN, CLOSE, TEXT, BINARY, PONG, SENDREADY
	}

	private final WebSocketExchange exchange;
	private final Type type;
	private final Object obj;

	WebSocketEvent(WebSocketExchange exchange, Type type, Object obj) {
		this.exchange = exchange;
		this.type = type;
		this.obj = obj;
	}

	public WebSocketExchange getWebSocketExchange() {
		return exchange;
	}

	public Type type() {
		return type;
	}

	public String getText() {
		return (String) obj;
	}

	public byte[] getBinary() {
		return (byte[]) obj;
	}

	@SuppressWarnings("unchecked")
	public Pair<Long, Long> getPong() {
		return (Pair<Long, Long>) obj;
	}

	@SuppressWarnings("unchecked")
	public Pair<Short, String> getClose() {
		return (Pair<Short, String>) obj;
	}
}