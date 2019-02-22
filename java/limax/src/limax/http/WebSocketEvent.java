package limax.http;

import limax.util.Pair;

public class WebSocketEvent {
	private final WebSocketExchange exchange;
	private final String text;
	private final byte[] binary;
	private final Pair<Long, Long> pong;
	private final Pair<Short, String> close;

	WebSocketEvent(WebSocketExchange exchange, String text, byte[] binary, Pair<Long, Long> pong,
			Pair<Short, String> close) {
		this.exchange = exchange;
		this.text = text;
		this.binary = binary;
		this.pong = pong;
		this.close = close;
	}

	public WebSocketExchange getWebSocketExchange() {
		return exchange;
	}

	public boolean isText() {
		return text != null;
	}

	public boolean isBinary() {
		return binary != null;
	}

	public boolean isPong() {
		return pong != null;
	}

	public boolean isClose() {
		return close != null;
	}

	public String getText() {
		if (text == null)
			throw new UnsupportedOperationException();
		return text;
	}

	public byte[] getBinary() {
		if (binary == null)
			throw new UnsupportedOperationException();
		return binary;
	}

	public Pair<Long, Long> getPong() {
		if (pong == null)
			throw new UnsupportedOperationException();
		return pong;
	}

	public Pair<Short, String> getClose() {
		if (close == null)
			throw new UnsupportedOperationException();
		return close;
	}
}