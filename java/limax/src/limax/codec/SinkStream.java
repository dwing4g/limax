package limax.codec;

import java.io.IOException;
import java.io.OutputStream;

public final class SinkStream implements Codec {
	private final OutputStream out;

	public SinkStream(OutputStream out) {
		this.out = out;
	}

	@Override
	public void update(byte c) throws CodecException {
		try {
			out.write(c);
		} catch (IOException e) {
			throw new CodecException(e);
		}
	}

	@Override
	public void update(byte[] data, int off, int len) throws CodecException {
		try {
			out.write(data, off, len);
		} catch (IOException e) {
			throw new CodecException(e);
		}
	}

	@Override
	public void flush() throws CodecException {
		try {
			out.flush();
		} catch (IOException e) {
			throw new CodecException(e);
		}
	}
}
