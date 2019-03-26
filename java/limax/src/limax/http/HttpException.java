package limax.http;

public class HttpException extends RuntimeException {
	private static final long serialVersionUID = 2972607535345793937L;

	private final HttpHandler handler;
	private final boolean forceClose;

	public HttpException(HttpHandler handler, boolean forceClose) {
		this.handler = handler;
		this.forceClose = forceClose;
	}

	public HttpException(int statusCode, boolean forceClose) {
		this(exchange -> {
			exchange.getResponseHeaders().set(":status", statusCode);
			return null;
		}, forceClose);
	}

	HttpHandler getHandler() {
		return handler;
	}

	boolean isForceClose() {
		return forceClose;
	}
}
