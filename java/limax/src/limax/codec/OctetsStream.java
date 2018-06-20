package limax.codec;

import java.nio.charset.Charset;

public final class OctetsStream extends Octets {
	private static Charset DEFAULT_CHARSET = Charset.forName("UTF-8");

	private static final long serialVersionUID = 5287217526924957555L;
	private static final int MAXSPARE = 8192;
	private int pos = 0;
	private int tranpos = 0;

	public OctetsStream() {
	}

	public OctetsStream(int size) {
		super(size);
	}

	public OctetsStream(Octets o) {
		super(o);
	}

	private OctetsStream(byte[] bytes, int length) {
		super(bytes, length);
	}

	public static OctetsStream wrap(Octets o) {
		return new OctetsStream(o.array(), o.size());
	}

	@Override
	public String toString() {
		return "OctetsStream.size=" + size() + " pos = " + pos + " tranpos = " + tranpos;
	}

	public final boolean eos() {
		return pos == size();
	}

	public final OctetsStream position(int pos) {
		this.pos = pos;
		return this;
	}

	public final int position() {
		return pos;
	}

	public final int remain() {
		return size() - pos;
	}

	public OctetsStream begin() {
		tranpos = pos;
		return this;
	}

	public OctetsStream rollback() {
		pos = tranpos;
		return this;
	}

	public OctetsStream commit() {
		if (pos >= MAXSPARE) {
			erase(0, pos);
			pos = 0;
		}
		return this;
	}

	public OctetsStream marshal(byte x) {
		push_byte(x);
		return this;
	}

	public OctetsStream marshal(boolean b) {
		push_byte((byte) (b ? 1 : 0));
		return this;
	}

	public OctetsStream marshal(short x) {
		return marshal((byte) (x >> 8)).marshal((byte) (x));
	}

	public OctetsStream marshal(char x) {
		return marshal((byte) (x >> 8)).marshal((byte) (x));
	}

	public OctetsStream marshal(int x) {
		return marshal((byte) (x >> 24)).marshal((byte) (x >> 16)).marshal((byte) (x >> 8)).marshal((byte) (x));
	}

	public OctetsStream marshal(long x) {
		return marshal((byte) (x >> 56)).marshal((byte) (x >> 48)).marshal((byte) (x >> 40)).marshal((byte) (x >> 32))
				.marshal((byte) (x >> 24)).marshal((byte) (x >> 16)).marshal((byte) (x >> 8)).marshal((byte) (x));
	}

	public OctetsStream marshal(float x) {
		return marshal(Float.floatToRawIntBits(x));
	}

	public OctetsStream marshal(double x) {
		return marshal(Double.doubleToRawLongBits(x));
	}

	public OctetsStream marshal(Marshal m) {
		return m.marshal(this);
	}

	public OctetsStream marshal_size(int x) {
		if (x >= 0) {
			if (x < 0x80) // 0xxxxxxx
				return marshal((byte) x);
			if (x < 0x4000) // 10xxxxxx xxxxxxxx
				return marshal((byte) ((x >> 8) | 0x80)).marshal((byte) x);
			if (x < 0x200000) // 110xxxxx xxxxxxxx xxxxxxxx
				return marshal((byte) ((x >> 16) | 0xc0)).marshal((byte) (x >> 8)).marshal((byte) x);
			if (x < 0x10000000) // 1110xxxx xxxxxxxx xxxxxxxx xxxxxxxx
				return marshal((byte) ((x >> 24) | 0xe0)).marshal((byte) (x >> 16)).marshal((byte) (x >> 8))
						.marshal((byte) x);
		}
		return marshal((byte) 0xf0).marshal(x);
	}

	public OctetsStream marshal(Octets o) {
		this.marshal_size(o.size());
		insert(size(), o);
		return this;
	}

	public OctetsStream marshal(byte[] bytes) {
		this.marshal_size(bytes.length);
		insert(size(), bytes);
		return this;
	}

	public OctetsStream marshal(String str) {
		return marshal(str, DEFAULT_CHARSET);
	}

	public OctetsStream marshal(String str, Charset charset) {
		marshal(str.getBytes(charset));
		return this;
	}

	public byte unmarshal_byte() throws MarshalException {
		if (pos + 1 > size())
			throw new MarshalException();
		return getByte(pos++);
	}

	public boolean unmarshal_boolean() throws MarshalException {
		return unmarshal_byte() == 1;
	}

	public short unmarshal_short() throws MarshalException {
		if (pos + 2 > size())
			throw new MarshalException();
		byte b0 = getByte(pos++);
		byte b1 = getByte(pos++);
		return (short) ((b0 << 8) | (b1 & 0xff));
	}

	public char unmarshal_char() throws MarshalException {
		if (pos + 2 > size())
			throw new MarshalException();
		byte b0 = getByte(pos++);
		byte b1 = getByte(pos++);
		return (char) ((b0 << 8) | (b1 & 0xff));
	}

	public int unmarshal_int() throws MarshalException {
		if (pos + 4 > size())
			throw new MarshalException();
		byte b0 = getByte(pos++);
		byte b1 = getByte(pos++);
		byte b2 = getByte(pos++);
		byte b3 = getByte(pos++);
		return ((b0 & 0xff) << 24) | ((b1 & 0xff) << 16) | ((b2 & 0xff) << 8) | (b3 & 0xff);
	}

	public long unmarshal_long() throws MarshalException {
		if (pos + 8 > size())
			throw new MarshalException();
		byte b0 = getByte(pos++);
		byte b1 = getByte(pos++);
		byte b2 = getByte(pos++);
		byte b3 = getByte(pos++);
		byte b4 = getByte(pos++);
		byte b5 = getByte(pos++);
		byte b6 = getByte(pos++);
		byte b7 = getByte(pos++);
		return (((long) b0 & 0xff) << 56) | (((long) b1 & 0xff) << 48) | (((long) b2 & 0xff) << 40)
				| (((long) b3 & 0xff) << 32) | (((long) b4 & 0xff) << 24) | (((long) b5 & 0xff) << 16)
				| (((long) b6 & 0xff) << 8) | ((long) b7 & 0xff);
	}

	public float unmarshal_float() throws MarshalException {
		return Float.intBitsToFloat(unmarshal_int());
	}

	public double unmarshal_double() throws MarshalException {
		return Double.longBitsToDouble(unmarshal_long());
	}

	public int unmarshal_size() throws MarshalException {
		byte b0 = unmarshal_byte();
		if ((b0 & 0x80) == 0)
			return b0;
		if ((b0 & 0x40) == 0) {
			byte b1 = unmarshal_byte();
			return ((b0 & 0x3f) << 8) | (b1 & 0xff);
		}
		if ((b0 & 0x20) == 0) {
			byte b1 = unmarshal_byte();
			byte b2 = unmarshal_byte();
			return ((b0 & 0x1f) << 16) | ((b1 & 0xff) << 8) | (b2 & 0xff);
		}
		if ((b0 & 0x10) == 0) {
			byte b1 = unmarshal_byte();
			byte b2 = unmarshal_byte();
			byte b3 = unmarshal_byte();
			return ((b0 & 0x0f) << 24) | ((b1 & 0xff) << 16) | ((b2 & 0xff) << 8) | (b3 & 0xff);
		}
		return unmarshal_int();
	}

	public Octets unmarshal_Octets() throws MarshalException {
		int size = this.unmarshal_size();
		if (pos + size > size())
			throw new MarshalException();
		Octets o = new Octets(this, pos, size);
		pos += size;
		return o;
	}

	public byte[] unmarshal_bytes() throws MarshalException {
		int size = this.unmarshal_size();
		if (pos + size > size())
			throw new MarshalException();
		byte[] copy = new byte[size];
		System.arraycopy(array(), pos, copy, 0, size);
		pos += size;
		return copy;
	}

	public String unmarshal_String() throws MarshalException {
		return unmarshal_String(DEFAULT_CHARSET);
	}

	public String unmarshal_String(Charset charset) throws MarshalException {
		try {
			int size = this.unmarshal_size();
			if (pos + size > size())
				throw new MarshalException();
			int cur = pos;
			pos += size;
			return (charset == null) ? new String(array(), cur, size) : new String(array(), cur, size, charset);
		} catch (Exception e) {
			throw new MarshalException(e);
		}
	}

	public OctetsStream unmarshal(Marshal m) throws MarshalException {
		return m.unmarshal(this);
	}

}
