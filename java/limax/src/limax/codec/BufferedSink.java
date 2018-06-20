package limax.codec;

public class BufferedSink implements Codec {
	private final Codec sink;
	private final byte[] buffer = new byte[8192];
	private int pos = 0;

	private void flushInternal() throws CodecException {
		if (pos > 0) {
			sink.update(buffer, 0, pos);
			pos = 0;
		}
	}

	public BufferedSink(Codec sink) {
		this.sink = sink;
	}

	@Override
	public void update(byte c) throws CodecException {
		if (buffer.length == pos) {
			flushInternal();
		}
		buffer[pos++] = c;
	}

	@Override
	public void update(byte[] data, int off, int len) throws CodecException {
		if (len >= buffer.length) {
			flushInternal();
			sink.update(data, off, len);
			return;
		}
		if (len > buffer.length - pos) {
			flushInternal();
		}
		System.arraycopy(data, off, buffer, pos, len);
		pos += len;
	}

	@Override
	public void flush() throws CodecException {
		flushInternal();
		sink.flush();
	}

}
