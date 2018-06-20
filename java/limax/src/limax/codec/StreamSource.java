package limax.codec;

import java.io.IOException;
import java.io.InputStream;

public final class StreamSource implements Source {
	private final Codec sink;
	private final InputStream is;

	public StreamSource(InputStream is, Codec sink) {
		this.sink = sink;
		this.is = is;
	}

	@Override
	public void flush() throws CodecException {
		byte[] data = new byte[8192];
		try {
			for (int len; (len = is.read(data)) != -1; len++)
				sink.update(data, 0, len);
		} catch (IOException e) {
			throw new CodecException(e);
		}
		sink.flush();
	}
}
