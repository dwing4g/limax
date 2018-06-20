package limax.node.js.modules.http;

import limax.codec.CodecException;

public class HttpException extends CodecException {

	private static final long serialVersionUID = -4114154845954446647L;

	private final int code;

	public HttpException(int code, String message) {
		super(message);
		this.code = code;
	}

	public HttpException(int code, Throwable t) {
		super(t);
		this.code = code;
	}

	public int getCode() {
		return code;
	}
}
