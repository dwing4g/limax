package limax.codec;

import java.security.MessageDigest;

abstract class MD implements Codec {
	private final Codec sink;
	protected MessageDigest md;

	public MD(Codec sink) {
		this.sink = sink;
	}

	@Override
	public void update(byte c) throws CodecException {
		byte[] data = new byte[] { c };
		md.update(data, 0, 1);
		sink.update(data, 0, 1);
	}

	@Override
	public void update(byte[] data, int off, int len) throws CodecException {
		md.update(data, off, len);
		sink.update(data, off, len);
	}

	@Override
	public void flush() throws CodecException {
		sink.flush();
	}

	public byte[] digest() {
		return md.digest();
	}
}
