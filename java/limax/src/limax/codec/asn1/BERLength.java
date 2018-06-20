package limax.codec.asn1;

import java.util.Arrays;

class BERLength {
	private int nbytes;
	private long length;

	public BERLength() {
		nbytes = 0;
	}

	public BERStage next(byte in) {
		if (nbytes == 0) {
			if (in == 0) {
				length = 0;
				return BERStage.END;
			}
			if (in > 0) {
				length = in;
				return BERStage.DEFINITE_CONTENT;
			}
			if (in == (byte) 0x80) {
				length = -1;
				return BERStage.INDEFINITE_CONTENT;
			}
			nbytes = in & 0x7f;
			if (nbytes > 7)
				throw new BERException("Length too long");
			length = 0;
			return BERStage.LENGTH;
		}
		length = (length << 8) | (in & 0xff);
		return --nbytes > 0 ? BERStage.LENGTH : BERStage.DEFINITE_CONTENT;
	}

	public long getLength() {
		return length;
	}

	public static int renderLength(long length) {
		if (length < 0)
			throw new BERException("Length too long");
		if (length < 128)
			return 1;
		int pos = 1;
		for (int i = 1; i <= 8; i++) {
			byte b = (byte) ((length >> (64 - i * 8)) & 0xff);
			if (pos == 1 && b == 0)
				continue;
			++pos;
		}
		return pos;
	}

	public static byte[] render(long length) {
		if (length < 0)
			throw new BERException("Length too long");
		if (length < 128) {
			byte[] r = new byte[] { (byte) length };
			return r;
		}
		int pos = 0;
		byte[] r = new byte[9];
		for (int i = 1; i <= 8; i++) {
			byte b = (byte) ((length >> (64 - i * 8)) & 0xff);
			if (pos == 0 && b == 0)
				continue;
			r[++pos] = b;
		}
		r[0] = (byte) (0x80 | pos);
		return Arrays.copyOf(r, pos + 1);
	}
}