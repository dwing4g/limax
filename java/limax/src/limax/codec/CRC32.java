package limax.codec;

public class CRC32 implements Codec {
	private Codec sink;
	java.util.zip.CRC32 crc = new java.util.zip.CRC32();

	public CRC32(Codec sink) {
		this.sink = sink;
	}

	public void update(byte c) throws CodecException {
		crc.update(c & 0xff);
		sink.update(c);
	}

	public void update(byte[] data, int off, int len) throws CodecException {
		crc.update(data, off, len);
		sink.update(data, off, len);
	}

	public void flush() throws CodecException {
		sink.flush();
	}

	public long getValue() {
		return crc.getValue();
	}
}
