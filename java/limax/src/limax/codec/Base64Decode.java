package limax.codec;

public class Base64Decode implements Codec {
	private static final byte[] DECODE = { (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x3e, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0x3f, (byte) 0x34, (byte) 0x35, (byte) 0x36, (byte) 0x37, (byte) 0x38, (byte) 0x39,
			(byte) 0x3a, (byte) 0x3b, (byte) 0x3c, (byte) 0x3d, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x7f,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
			(byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08, (byte) 0x09, (byte) 0x0a, (byte) 0x0b, (byte) 0x0c,
			(byte) 0x0d, (byte) 0x0e, (byte) 0x0f, (byte) 0x10, (byte) 0x11, (byte) 0x12, (byte) 0x13, (byte) 0x14,
			(byte) 0x15, (byte) 0x16, (byte) 0x17, (byte) 0x18, (byte) 0x19, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x1a, (byte) 0x1b, (byte) 0x1c, (byte) 0x1d, (byte) 0x1e,
			(byte) 0x1f, (byte) 0x20, (byte) 0x21, (byte) 0x22, (byte) 0x23, (byte) 0x24, (byte) 0x25, (byte) 0x26,
			(byte) 0x27, (byte) 0x28, (byte) 0x29, (byte) 0x2a, (byte) 0x2b, (byte) 0x2c, (byte) 0x2d, (byte) 0x2e,
			(byte) 0x2f, (byte) 0x30, (byte) 0x31, (byte) 0x32, (byte) 0x33, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
			(byte) 0xff, (byte) 0x00 };
	private static final byte B64PAD = '=';

	private final Codec sink;
	private int b0;
	private int b1;
	private int b2;
	private int b3;
	private int n;

	public Base64Decode(Codec sink) {
		this.sink = sink;
	}

	private int update0(byte[] r, int j, byte[] data, int off, int len) throws CodecException {
		for (n = len; n > 7; n -= 4) {
			b0 = DECODE[data[off++] & 0xff];
			b1 = DECODE[data[off++] & 0xff];
			b2 = DECODE[data[off++] & 0xff];
			b3 = DECODE[data[off++] & 0xff];
			if (b0 == 0xff || b1 == 0xff || b2 == 0xff || b3 == 0xff)
				throw new CodecException("bad base64 char");
			r[j++] = (byte) (b0 << 2 & 0xfc | b1 >> 4 & 0x3);
			r[j++] = (byte) (b1 << 4 & 0xf0 | b2 >> 2 & 0xf);
			r[j++] = (byte) (b2 << 6 & 0xc0 | b3 & 0x3f);
		}
		if (n > 3) {
			n -= 4;
			b0 = DECODE[data[off++] & 0xff];
			b1 = DECODE[data[off++] & 0xff];
			b2 = DECODE[data[off++] & 0xff];
			b3 = DECODE[data[off++] & 0xff];
			if (b0 == 0xff || b1 == 0xff || b2 == 0xff || b3 == 0xff)
				throw new CodecException("bad base64 char");
			if (b2 == 0x7f) {
				r[j++] = (byte) (b0 << 2 & 0xfc | b1 >> 4 & 0x3);
				return j;
			} else if (b3 == 0x7f) {
				r[j++] = (byte) (b0 << 2 & 0xfc | b1 >> 4 & 0x3);
				r[j++] = (byte) (b1 << 4 & 0xf0 | b2 >> 2 & 0xf);
				return j;
			}
			r[j++] = (byte) (b0 << 2 & 0xfc | b1 >> 4 & 0x3);
			r[j++] = (byte) (b1 << 4 & 0xf0 | b2 >> 2 & 0xf);
			r[j++] = (byte) (b2 << 6 & 0xc0 | b3 & 0x3f);
		}
		if (n == 1) {
			b0 = DECODE[data[off] & 0xff];
		} else if (n == 2) {
			b0 = DECODE[data[off] & 0xff];
			b1 = DECODE[data[off + 1] & 0xff];
		} else if (n == 3) {
			b0 = DECODE[data[off] & 0xff];
			b1 = DECODE[data[off + 1] & 0xff];
			b2 = DECODE[data[off + 2] & 0xff];
		}
		return j;
	}

	private int update1(byte[] r, byte[] data, int off, int len) throws CodecException {
		switch (len) {
		case 0:
			return 0;
		case 1:
			b1 = DECODE[data[off] & 0xff];
			n = 2;
			return 0;
		case 2:
			b1 = DECODE[data[off] & 0xff];
			b2 = DECODE[data[off + 1] & 0xff];
			n = 3;
			return 0;
		}
		b1 = DECODE[data[off] & 0xff];
		b2 = DECODE[data[off + 1] & 0xff];
		b3 = DECODE[data[off + 2] & 0xff];
		if (b0 == 0xff || b1 == 0xff || b2 == 0xff || b3 == 0xff)
			throw new CodecException("bad base64 char");
		if (b2 == 0x7f) {
			r[0] = (byte) (b0 << 2 & 0xfc | b1 >> 4 & 0x3);
			return 1;
		} else if (b3 == 0x7f) {
			r[0] = (byte) (b0 << 2 & 0xfc | b1 >> 4 & 0x3);
			r[1] = (byte) (b1 << 4 & 0xf0 | b2 >> 2 & 0xf);
			return 2;
		}
		r[0] = (byte) (b0 << 2 & 0xfc | b1 >> 4 & 0x3);
		r[1] = (byte) (b1 << 4 & 0xf0 | b2 >> 2 & 0xf);
		r[2] = (byte) (b2 << 6 & 0xc0 | b3 & 0x3f);
		return update0(r, 3, data, off + 3, len - 3);
	}

	private int update2(byte[] r, byte[] data, int off, int len) throws CodecException {
		switch (len) {
		case 0:
			return 0;
		case 1:
			b2 = DECODE[data[off] & 0xff];
			n = 3;
			return 0;
		}
		b2 = DECODE[data[off] & 0xff];
		b3 = DECODE[data[off + 1] & 0xff];
		if (b0 == 0xff || b1 == 0xff || b2 == 0xff || b3 == 0xff)
			throw new CodecException("bad base64 char");
		if (b2 == 0x7f) {
			r[0] = (byte) (b0 << 2 & 0xfc | b1 >> 4 & 0x3);
			return 1;
		} else if (b3 == 0x7f) {
			r[0] = (byte) (b0 << 2 & 0xfc | b1 >> 4 & 0x3);
			r[1] = (byte) (b1 << 4 & 0xf0 | b2 >> 2 & 0xf);
			return 2;
		}
		r[0] = (byte) (b0 << 2 & 0xfc | b1 >> 4 & 0x3);
		r[1] = (byte) (b1 << 4 & 0xf0 | b2 >> 2 & 0xf);
		r[2] = (byte) (b2 << 6 & 0xc0 | b3 & 0x3f);
		return update0(r, 3, data, off + 2, len - 2);
	}

	private int update3(byte[] r, byte[] data, int off, int len) throws CodecException {
		if (len == 0)
			return 0;
		b3 = DECODE[data[off] & 0xff];
		if (b0 == 0xff || b1 == 0xff || b2 == 0xff || b3 == 0xff)
			throw new CodecException("bad base64 char");
		if (b2 == 0x7f) {
			r[0] = (byte) (b0 << 2 & 0xfc | b1 >> 4 & 0x3);
			return 1;
		} else if (b3 == 0x7f) {
			r[0] = (byte) (b0 << 2 & 0xfc | b1 >> 4 & 0x3);
			r[1] = (byte) (b1 << 4 & 0xf0 | b2 >> 2 & 0xf);
			return 2;
		}
		r[0] = (byte) (b0 << 2 & 0xfc | b1 >> 4 & 0x3);
		r[1] = (byte) (b1 << 4 & 0xf0 | b2 >> 2 & 0xf);
		r[2] = (byte) (b2 << 6 & 0xc0 | b3 & 0x3f);
		return update0(r, 3, data, off + 1, len - 1);
	}

	private int update(byte[] r, byte[] data, int off, int len) throws CodecException {
		switch (n) {
		case 0:
			return update0(r, 0, data, off, len);
		case 1:
			return update1(r, data, off, len);
		case 2:
			return update2(r, data, off, len);
		}
		return update3(r, data, off, len);
	}

	@Override
	public void update(byte[] data, int off, int len) throws CodecException {
		int length = (n + len) / 4 * 3;
		byte[] r = new byte[length];
		sink.update(r, 0, Math.min(update(r, data, off, len), length));
	}

	@Override
	public void update(byte c) throws CodecException {
		update(new byte[] { c }, 0, 1);
	}

	@Override
	public void flush() throws CodecException {
		sink.flush();
		n = 0;
	}

	public static byte[] transform(byte[] data) throws CodecException {
		int len = data.length / 4 * 3;
		if (data[data.length - 1] == B64PAD)
			len--;
		if (data[data.length - 2] == B64PAD)
			len--;
		byte[] r = new byte[len];
		new Base64Decode(null).update(r, data, 0, data.length);
		return r;
	}
}
