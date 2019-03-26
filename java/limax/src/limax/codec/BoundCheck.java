package limax.codec;

public final class BoundCheck implements Codec {
	private long bytes;
	private final long deadline;
	private final Codec sink;

	public BoundCheck(long bytes, long milliseconds, Codec sink) {
		this.bytes = bytes;
		long s = milliseconds + System.currentTimeMillis();
		this.deadline = s < 0 ? Long.MAX_VALUE : s;
		this.sink = sink;
	}

	private void check(int len) throws CodecException {
		if ((bytes -= len) < 0)
			throw new CodecException("BoundCheck overflow");
		if (System.currentTimeMillis() > deadline)
			throw new CodecException("BoundCheck deadline");
	}

	@Override
	public void update(byte c) throws CodecException {
		check(1);
		sink.update(c);
	}

	@Override
	public void update(byte[] data, int off, int len) throws CodecException {
		check(len);
		sink.update(data, off, len);
	}

	@Override
	public void flush() throws CodecException {
		check(0);
		sink.flush();
	}

}
