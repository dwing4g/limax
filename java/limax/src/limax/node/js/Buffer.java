package limax.node.js;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Base64;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import limax.util.Helper;

public final class Buffer extends AbstractList<Object> implements Module, JSONStringifiable {
	public static final Buffer EMPTY = new Buffer(0);

	public static class Data {
		public final byte[] buf;
		public final int off;
		public final int len;

		public Data(ByteBuffer bb) {
			len = bb.remaining();
			if (bb.hasArray()) {
				buf = bb.array();
				off = bb.position();
			} else {
				bb.duplicate().get(buf = new byte[len]);
				off = 0;
			}
		}

		public Data(byte[] buf, int off, int len) {
			this.buf = buf;
			this.off = off;
			this.len = len;
		}
	}

	public final ByteBuffer bb;
	private static Object _Buffer;

	static synchronized void load(ScriptEngine engine) throws ScriptException, IOException {
		if (_Buffer == null) {
			try (Reader reader = new InputStreamReader(Buffer.class.getResourceAsStream("Buffer.js"))) {
				_Buffer = engine.eval(reader);
			}
		} else {
			engine.put("Buffer", _Buffer);
		}
		engine.put("SlowBuffer", _Buffer);
	}

	public static Charset translateEncoding(String encoding) {
		switch (encoding.toLowerCase()) {
		case "utf8":
			return StandardCharsets.UTF_8;
		case "ucs2":
		case "utf16le":
			return StandardCharsets.UTF_16LE;
		case "ascii":
			return StandardCharsets.US_ASCII;
		case "latin1":
		case "binary":
			return StandardCharsets.ISO_8859_1;
		}
		throw new IllegalArgumentException("Unknown Encoding: " + encoding);
	}

	private Buffer(Buffer buffer, int start, int end) {
		if (start < 0)
			start += buffer.size();
		if (end < 0)
			end += buffer.size();
		ByteBuffer bb = buffer.bb.duplicate();
		bb.position(start);
		bb.limit(end);
		this.bb = bb.slice();
	}

	public Buffer(ByteBuffer bb) {
		this.bb = bb.position() == 0 ? bb : bb.slice();
	}

	public Buffer(byte[] data) {
		this.bb = ByteBuffer.wrap(data);
	}

	public Buffer(byte[] data, int off, int len) {
		this.bb = ByteBuffer.wrap(data, off, len);
	}

	public Buffer(Object[] data) {
		bb = ByteBuffer.allocateDirect(data.length);
		for (Object o : data)
			bb.put(o instanceof Number ? ((Number) o).byteValue() : (byte) 0);
		bb.rewind();
	}

	public Buffer(Buffer buffer) {
		bb = ByteBuffer.allocateDirect(buffer.size());
		bb.put(buffer.bb.duplicate());
		bb.rewind();
	}

	public Buffer(Number size) {
		bb = ByteBuffer.allocateDirect(Math.max(size.intValue(), 0));
	}

	public Buffer(String str, String encoding) {
		switch (encoding.toLowerCase()) {
		case "base64":
			this.bb = ByteBuffer.wrap(Base64.getDecoder().decode(str));
			return;
		case "hex":
			this.bb = ByteBuffer.wrap(Helper.fromHexString(str));
			return;
		case "undefined":
			encoding = "utf8";
		default:
			this.bb = translateEncoding(encoding).encode(str);
		}
	}

	public Buffer(String str) {
		this(str, "utf8");
	}

	@Override
	public Object get(int index) {
		return bb.get(index) & 0xff;
	}

	@Override
	public Object set(int index, Object value) {
		if (value instanceof Number)
			bb.put(index, ((Number) value).byteValue());
		return null;
	}

	public int compare(Buffer otherBuffer) {
		int l0 = size();
		int l1 = otherBuffer.size();
		int l = Math.min(l0, l1);
		for (int i = 0; i < l; i++) {
			byte cb = bb.get(i);
			byte ob = otherBuffer.bb.get(i);
			if (cb < ob)
				return -1;
			if (cb > ob)
				return 1;
		}
		if (l0 < l1)
			return -1;
		if (l0 > l1)
			return 1;
		return 0;
	}

	public static Object alloc;
	public static Object allocUnsafe;
	public static Object allocUnsafeSlow;
	public static Object byteLength;
	public static Object compare;
	public static Object concat;
	public static Object from;
	public static Object isBuffer;
	public static Object isEncoding;

	public void copy(Buffer targetBuffer, Number targetStart, Number sourceStart, Number sourceEnd) {
		ByteBuffer target = targetBuffer.bb.duplicate();
		ByteBuffer source = bb.duplicate();
		source.position(sourceStart.intValue());
		source.limit(sourceEnd.intValue());
		target.position(targetStart.intValue());
		target.put(source);
	}

	public void copy(Buffer targetBuffer, Number targetStart, Number sourceStart) {
		copy(targetBuffer, targetStart, sourceStart, size());
	}

	public void copy(Buffer targetBuffer, Number targetStart) {
		copy(targetBuffer, targetStart, 0);
	}

	public void copy(Buffer targetBuffer) {
		copy(targetBuffer, 0);
	}

	@Override
	public boolean equals(Object otherBuffer) {
		return otherBuffer instanceof Buffer ? compare((Buffer) otherBuffer) == 0 : false;
	}

	private Buffer fill(Buffer src, Number offset, Number end) {
		int j = 0;
		ByteBuffer b = src.bb;
		int len = src.size();
		for (int i = offset.intValue(), e = end.intValue(); i < e; i++) {
			bb.put(i, b.get(j));
			if (++j == len)
				j = 0;
		}
		return this;
	}

	public Buffer fill(Object value, Number offset, Number end, String encoding) {
		if (value instanceof Number) {
			Number v = (Number) value;
			for (int i = offset.intValue(), e = end.intValue(); i < e; i++)
				bb.put(i, v.byteValue());
		} else if (value instanceof String)
			fill(new Buffer((String) value, encoding), offset, end);
		else if (value instanceof Buffer)
			fill((Buffer) value, offset, end);
		return this;

	}

	public Buffer fill(Object value, Number offset, Number end) {
		return fill(value, offset, end, "utf8");
	}

	public Buffer fill(Object value, Number offset) {
		return fill(value, offset, size());
	}

	public Buffer fill(Object value) {
		return fill(value, 0);
	}

	public int indexOf(Object value, Number byteOffset, String encoding) {
		int off = byteOffset.intValue();
		if (off < 0)
			off += size();
		if (value instanceof Number) {
			byte v = ((Number) value).byteValue();
			for (int i = off; i < size(); i++)
				if (v == bb.get(i))
					return i;
			return -1;
		}
		Buffer b;
		if (value instanceof String) {
			b = new Buffer((String) value, encoding);
		} else if (value instanceof Buffer) {
			b = (Buffer) value;
		} else
			return -1;
		try {
			int r = toString("binary", off).indexOf(b.toString("binary"));
			if (r >= 0)
				return r + off;
		} catch (ScriptException e) {
		}
		return -1;
	}

	public int indexOf(Object value, Number byteOffset) {
		return indexOf(value, byteOffset, "utf8");
	}

	public int indexOf(Object value) {
		return indexOf(value, 0);
	}

	public boolean includes(Object value, Number byteOffset, String encoding) {
		return indexOf(value, byteOffset, encoding) != -1;
	}

	public boolean includes(Object value, Number byteOffset) {
		return includes(value, byteOffset, "utf8");
	}

	public boolean includes(Object value) {
		return includes(value, 0);
	}

	public int getLength() {
		return size();
	}

	public void setLength(int length) {
		bb.limit(length);
	}

	private ByteBuffer getBuffer(ByteOrder bo) {
		ByteBuffer b = bb.duplicate();
		b.order(bo);
		return b;
	}

	public double readDoubleBE(Number offset) {
		return getBuffer(ByteOrder.BIG_ENDIAN).getDouble(offset.intValue());
	}

	public double readDoubleBE() {
		return readDoubleBE(0);
	}

	public double readDoubleLE(Number offset) {
		return getBuffer(ByteOrder.LITTLE_ENDIAN).getDouble(offset.intValue());
	}

	public double readDoubleLE() {
		return readDoubleLE(0);
	}

	public Float readFloatBE(Number offset) {
		return getBuffer(ByteOrder.BIG_ENDIAN).getFloat(offset.intValue());
	}

	public Float readFloatBE() {
		return readFloatBE(0);
	}

	public Float readFloatLE(Number offset) {
		return getBuffer(ByteOrder.LITTLE_ENDIAN).getFloat(offset.intValue());
	}

	public Float readFloatLE() {
		return readFloatLE(0);
	}

	public int readInt8(Number offset) {
		return bb.get(offset.intValue());
	}

	public int readInt8() {
		return readInt8(0);
	}

	public int readInt16BE(Number offset) {
		return getBuffer(ByteOrder.BIG_ENDIAN).getShort(offset.intValue());
	}

	public int readInt16BE() {
		return readInt16BE(0);
	}

	public int readInt16LE(Number offset) {
		return getBuffer(ByteOrder.LITTLE_ENDIAN).getShort(offset.intValue());
	}

	public int readInt16LE() {
		return readInt16LE(0);
	}

	public int readInt32BE(Number offset) {
		return getBuffer(ByteOrder.BIG_ENDIAN).getInt(offset.intValue());
	}

	public int readInt32BE() {
		return readInt32BE(0);
	}

	public int readInt32LE(Number offset) {
		return getBuffer(ByteOrder.LITTLE_ENDIAN).getInt(offset.intValue());
	}

	public int readInt32LE() {
		return readInt32LE(0);
	}

	public Number readIntBE(Number offset, Number byteLength) {
		int s = offset.intValue(), e = s + byteLength.intValue();
		if (s >= e)
			return 0;
		long r = (bb.get(s) & 0x80) != 0 ? -1 : 0;
		for (; s < e; s++)
			r = (r << 8) | bb.get(s) & 0xff;
		return (double) r;
	}

	public Number readIntLE(Number offset, Number byteLength) {
		int s = offset.intValue(), e = s + byteLength.intValue();
		if (s >= e)
			return 0;
		long r = (bb.get(e - s - 1) & 0x80) != 0 ? -1 : 0;
		for (; s < e; s++)
			r = (r << 8) | bb.get(e - s - 1) & 0xff;
		return (double) r;
	}

	public int readUInt8(Number offset) {
		return bb.get(offset.intValue()) & 0xff;
	}

	public int readUInt8() {
		return readUInt8(0);
	}

	public int readUInt16BE(Number offset) {
		return getBuffer(ByteOrder.BIG_ENDIAN).getShort(offset.intValue()) & 0xffff;
	}

	public int readUInt16BE() {
		return readUInt16BE(0);
	}

	public int readUInt16LE(Number offset) {
		return getBuffer(ByteOrder.LITTLE_ENDIAN).getShort(offset.intValue()) & 0xffff;
	}

	public int readUInt16LE() {
		return readUInt16LE(0);
	}

	public long readUInt32BE(Number offset) {
		return Integer.toUnsignedLong(getBuffer(ByteOrder.BIG_ENDIAN).getInt(offset.intValue()));
	}

	public long readUInt32BE() {
		return readUInt32BE(0);
	}

	public long readUInt32LE(Number offset) {
		return Integer.toUnsignedLong(getBuffer(ByteOrder.LITTLE_ENDIAN).getInt(offset.intValue()));
	}

	public long readUInt32LE() {
		return readUInt32LE(0);
	}

	public Number readUIntBE(Number offset, Number byteLength) {
		long r = 0;
		for (int s = offset.intValue(), e = s + byteLength.intValue(); s < e; s++)
			r = (r << 8) | bb.get(s) & 0xff;
		return (double) r;
	}

	public Number readUIntLE(Number offset, Number byteLength) {
		long r = 0;
		for (int s = offset.intValue(), e = s + byteLength.intValue(); s < e; s++)
			r = (r << 8) | bb.get(e - s - 1) & 0xff;
		return (double) r;
	}

	public Buffer slice(Number start, Number end) {
		return new Buffer(this, start.intValue(), end.intValue());
	}

	public Buffer slice(Number start) {
		return slice(start, size());
	}

	public Buffer slice() {
		return slice(0);
	}

	public String toString(String encoding, Number start, Number end) {
		ByteBuffer bb = this.bb.duplicate();
		int s = start.intValue();
		int e = end.intValue();
		bb.position(s);
		bb.limit(e);
		switch (encoding.toLowerCase()) {
		case "hex": {
			byte[] d = new byte[e - s];
			bb.get(d);
			return Helper.toHexString(d);
		}
		case "base64": {
			byte[] d = new byte[e - s];
			bb.get(d);
			return Base64.getEncoder().encodeToString(d);
		}
		case "undefined":
			encoding = "utf8";
		default:
			return translateEncoding(encoding).decode(bb).toString();
		}
	}

	public String toString(String encoding, Number start) throws ScriptException {
		return toString(encoding, start, size());
	}

	public String toString(String encoding) throws ScriptException {
		return toString(encoding, 0);
	}

	@Override
	public String toString() {
		try {
			return toString("utf8");
		} catch (ScriptException e) {
			return toStringRaw();
		}
	}

	public String toStringRaw() {
		StringBuilder sb = new StringBuilder("<Buffer");
		for (int i = 0; i < size(); i++)
			sb.append(String.format(" %02x", bb.get(i)));
		sb.append(">");
		return sb.toString();
	}

	@Override
	public String toJSON() {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < size(); i++)
			sb.append(String.format("%d,", bb.get(i) & 0xff));
		if (sb.length() > 1)
			sb.setCharAt(sb.length() - 1, ']');
		else
			sb.append("]");
		return wrapData(sb.toString());
	}

	public ByteBuffer toByteBuffer() {
		return bb.duplicate();
	}

	public byte[] toByteArray() {
		if (bb.hasArray()) {
			if (bb.limit() == bb.capacity())
				return bb.array();
			else
				return Arrays.copyOf(bb.array(), bb.limit());
		}
		byte[] data = new byte[bb.limit()];
		bb.duplicate().get(data);
		return data;
	}

	public Data toData() {
		return new Data(bb);
	}

	public int write(String string, Number offset, Number length, String encoding) {
		int off = offset.intValue();
		int len = length.intValue();
		if (len < 0)
			len = size() - off;
		if (len == 0)
			return 0;
		int cap = size() - off;
		int rem = Math.min(len, cap);
		ByteBuffer b = bb.duplicate();
		b.position(off).limit(off + rem);
		Charset charset = null;
		byte[] data = null;
		switch (encoding.toLowerCase()) {
		case "hex":
			data = Helper.fromHexString(string);
			break;
		case "base64":
			data = Base64.getDecoder().decode(string);
			break;
		case "undefied":
			encoding = "utf8";
		default:
			charset = translateEncoding(encoding);
		}
		if (charset != null)
			charset.newEncoder().encode(CharBuffer.wrap(string), b, true);
		else
			b.put(data, 0, Math.min(data.length, rem));
		return b.position() - off;
	}

	public int write(String string, Number offset, Number length) {
		return write(string, offset, length, "utf8");
	}

	public int write(String string, Number offset) {
		return write(string, offset, size() - offset.intValue());
	}

	public int write(String string) {
		return write(string, 0);
	}

	public int writeDoubleBE(Number value, Number offset) {
		int off = offset.intValue();
		ByteBuffer b = getBuffer(ByteOrder.BIG_ENDIAN);
		b.putDouble(off, value.doubleValue());
		return off + 8;
	}

	public int writeDoubleLE(Number value, Number offset) {
		int off = offset.intValue();
		ByteBuffer b = getBuffer(ByteOrder.LITTLE_ENDIAN);
		b.putDouble(off, value.doubleValue());
		return off + 8;
	}

	public int writeFloatBE(Number value, Number offset) {
		int off = offset.intValue();
		ByteBuffer b = getBuffer(ByteOrder.BIG_ENDIAN);
		b.putFloat(off, value.floatValue());
		return off + 4;
	}

	public int writeFloatLE(Number value, Number offset) {
		int off = offset.intValue();
		ByteBuffer b = getBuffer(ByteOrder.LITTLE_ENDIAN);
		b.putFloat(off, value.floatValue());
		return off + 4;
	}

	public int writeInt8(Number value, Number offset) {
		int off = offset.intValue();
		bb.put(off, value.byteValue());
		return off + 1;
	}

	public int writeInt16BE(Number value, Number offset) {
		int off = offset.intValue();
		ByteBuffer b = getBuffer(ByteOrder.BIG_ENDIAN);
		b.putShort(off, value.shortValue());
		return off + 2;
	}

	public int writeInt16LE(Number value, Number offset) {
		int off = offset.intValue();
		ByteBuffer b = getBuffer(ByteOrder.LITTLE_ENDIAN);
		b.putShort(off, value.shortValue());
		return off + 2;
	}

	public int writeInt32BE(Number value, Number offset) {
		int off = offset.intValue();
		ByteBuffer b = getBuffer(ByteOrder.BIG_ENDIAN);
		b.putInt(off, value.intValue());
		return off + 4;
	}

	public int writeInt32LE(Number value, Number offset) {
		int off = offset.intValue();
		ByteBuffer b = getBuffer(ByteOrder.LITTLE_ENDIAN);
		b.putInt(off, value.intValue());
		return off + 4;
	}

	public int writeIntBE(Number value, Number offset, Number byteLength) {
		int off = offset.intValue();
		int len = byteLength.intValue();
		int end = off + len;
		long n = value.longValue();
		for (int i = end; i > off; n >>= 8)
			bb.put(--i, (byte) (n & 0xff));
		return end;
	}

	public int writeIntLE(Number value, Number offset, Number byteLength) {
		int off = offset.intValue();
		int len = byteLength.intValue();
		int end = off + len;
		long n = value.longValue();
		for (int i = off; i < end; i++, n >>= 8)
			bb.put(i, (byte) (n & 0xff));
		return end;
	}

	public int writeUInt8(Number value, Number offset) {
		return writeInt8(value, offset);
	}

	public int writeUInt16BE(Number value, Number offset) {
		return writeInt16BE(value, offset);
	}

	public int writeUInt16LE(Number value, Number offset) {
		return writeInt16LE(value, offset);
	}

	public int writeUInt32BE(Number value, Number offset) {
		return writeInt32BE(value, offset);
	}

	public int writeUInt32LE(Number value, Number offset) {
		return writeInt32LE(value, offset);
	}

	public int writeUIntBE(Number value, Number offset, Number byteLength) {
		return writeIntBE(value, offset, byteLength);
	}

	public int writeUIntLE(Number value, Number offset, Number byteLength) {
		return writeIntLE(value, offset, byteLength);
	}

	@Override
	public int size() {
		return bb.limit();
	}
}
