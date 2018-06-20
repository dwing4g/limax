package limax.provider;

public final class TunnelException extends Exception {
	public enum Type {
		NETWORK, CODEC, EXPIRE, LABEL
	};

	private final Type type;
	private final Exception exception;

	private static final long serialVersionUID = -976611466007860042L;

	TunnelException(Type type, Exception exception) {
		this.type = type;
		this.exception = exception;
	}

	TunnelException(Type type) {
		this(type, null);
	}

	public Type getType() {
		return type;
	}

	public Exception getException() {
		return exception;
	}

	@Override
	public String toString() {
		return "[TunnelException " + type + "]" + (exception == null ? "" : exception);
	}
}
