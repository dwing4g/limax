package limax.codec;

public final class ExceptionJail implements Codec {
	private final Codec sink;
	private volatile Exception exception;

	public ExceptionJail(Codec sink) {
		this.sink = sink;
	}

	@Override
	public void update(byte c) {
		try {
			if (exception == null)
				sink.update(c);
		} catch (Exception e) {
			exception = e;
		}
	}

	@Override
	public void update(byte[] data, int off, int len) {
		try {
			if (exception == null)
				sink.update(data, off, len);
		} catch (Exception e) {
			exception = e;
		}
	}

	@Override
	public void flush() {
		try {
			if (exception == null)
				sink.flush();
		} catch (Exception e) {
			exception = e;
		}
	}

	public Exception get() {
		return exception;
	}
}
