package limax.codec;

import javax.crypto.Mac;

abstract class Hmac implements Codec {
	private final Codec sink;
	protected Mac mac;

	public Hmac(Codec sink) {
		this.sink = sink;
	}

	@Override
	public void update(byte c) throws CodecException {
		byte[] data = new byte[] { c };
		mac.update(data, 0, 1);
		sink.update(data, 0, 1);
	}

	@Override
	public void update(byte[] data, int off, int len) throws CodecException {
		mac.update(data, off, len);
		sink.update(data, off, len);
	}

	@Override
	public void flush() throws CodecException {
		sink.flush();
	}

	public byte[] digest() {
		return mac.doFinal();
	}

}
