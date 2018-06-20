package limax.codec.asn1;

import limax.codec.Codec;
import limax.codec.CodecException;

public class EncodeDER implements Codec {
	private final Codec sink;

	public EncodeDER(Codec sink) {
		this.sink = sink;
	}

	@Override
	public void update(byte[] data, int off, int len) throws CodecException {
		sink.update(data, off, len);
	}

	@Override
	public void update(byte c) throws CodecException {
		update(new byte[] { c }, 0, 1);
	}

	@Override
	public void flush() throws CodecException {
		sink.flush();
	}
}
