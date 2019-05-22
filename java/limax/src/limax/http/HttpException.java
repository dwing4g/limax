package limax.http;

public class HttpException extends RuntimeException {
	private static final long serialVersionUID = 2972607535345793937L;

	private final HttpHandler handler;
	private final boolean forceClose;
	private final boolean abort;

	private HttpException(HttpHandler handler, boolean forceClose, boolean abort) {
		this.handler = handler;
		this.forceClose = forceClose;
		this.abort = abort;
	}

	public HttpException(HttpHandler handler, boolean forceClose) {
		this(handler, forceClose, false);
	}

	public HttpException(int statusCode, boolean forceClose) {
		this(exchange -> {
			exchange.getResponseHeaders().set(":status", statusCode);
			return null;
		}, forceClose);
	}

	public HttpException() {
		this(null, false, true);
	}

	HttpHandler getHandler() {
		return handler;
	}

	boolean isForceClose() {
		return forceClose;
	}

	boolean isAbort() {
		return abort;
	}
}
