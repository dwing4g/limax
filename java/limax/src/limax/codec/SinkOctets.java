package limax.codec;

public class SinkOctets implements Codec {
	private final Octets o;

	public SinkOctets(Octets o) {
		this.o = o;
	}

	@Override
	public void update(byte c) throws CodecException {
		o.push_byte(c);
	}

	@Override
	public void update(byte[] data, int off, int len) throws CodecException {
		o.append(data, off, len);
	}

	@Override
	public void flush() throws CodecException {
	}
}
