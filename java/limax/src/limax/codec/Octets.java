package limax.codec;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Octets implements Comparable<Octets>, Serializable {
	private static final long serialVersionUID = -6683312389154167084L;
	private static final int DEFAULT_SIZE = 128;
	private byte[] buffer = null;
	private int count = 0;

	private byte[] roundup(int size) {
		int capacity = DEFAULT_SIZE;
		while (size > capacity)
			capacity <<= 1;
		return new byte[capacity];
	}

	public void reserve(int size) {
		if (buffer == null) {
			buffer = roundup(size);
		} else if (size > buffer.length) {
			byte[] tmp = roundup(size);
			System.arraycopy(buffer, 0, tmp, 0, count);
			buffer = tmp;
		}
	}

	public Octets replace(byte[] data, int pos, int size) {
		reserve(size);
		System.arraycopy(data, pos, buffer, 0, size);
		count = size;
		return this;
	}

	public Octets replace(Octets data, int pos, int size) {
		return replace(data.buffer, pos, size);
	}

	public Octets replace(byte[] data) {
		return replace(data, 0, data.length);
	}

	public Octets replace(Octets data) {
		return replace(data.buffer, 0, data.count);
	}

	public Octets() {
		reserve(DEFAULT_SIZE);
	}

	public Octets(int size) {
		reserve(size);
	}

	public Octets(Octets rhs) {
		replace(rhs);
	}

	public Octets(byte[] rhs) {
		replace(rhs);
	}

	Octets(byte[] bytes, int length) {
		this.buffer = bytes;
		this.count = length;
	}

	public static Octets wrap(byte[] bytes, int length) {
		return new Octets(bytes, length);
	}

	public static Octets wrap(byte[] bytes) {
		return wrap(bytes, bytes.length);
	}

	public Octets(byte[] rhs, int pos, int size) {
		replace(rhs, pos, size);
	}

	public Octets(Octets rhs, int pos, int size) {
		replace(rhs, pos, size);
	}

	public Octets resize(int size) {
		reserve(size);
		count = size;
		return this;
	}

	public Octets fit() {
		buffer = Arrays.copyOf(buffer, count);
		return this;
	}

	public int size() {
		return count;
	}

	public int capacity() {
		return buffer.length;
	}

	public Octets clear() {
		count = 0;
		return this;
	}

	public Octets swap(Octets rhs) {
		int size = count;
		count = rhs.count;
		rhs.count = size;
		byte[] tmp = rhs.buffer;
		rhs.buffer = buffer;
		buffer = tmp;
		return this;
	}

	public Octets push_byte(byte data) {
		reserve(count + 1);
		buffer[count++] = data;
		return this;
	}

	public Octets erase(int from, int to) {
		System.arraycopy(buffer, to, buffer, from, count - to);
		count -= to - from;
		return this;
	}

	public Octets insert(int from, byte[] data, int pos, int size) {
		reserve(count + size);
		System.arraycopy(buffer, from, buffer, from + size, count - from);
		System.arraycopy(data, pos, buffer, from, size);
		count += size;
		return this;
	}

	public Octets insert(int from, Octets data, int pos, int size) {
		return insert(from, data.buffer, pos, size);
	}

	public Octets insert(int from, byte[] data) {
		return insert(from, data, 0, data.length);
	}

	public Octets insert(int from, Octets data) {
		return insert(from, data.buffer, 0, data.size());
	}

	public Octets append(byte[] data, int pos, int size) {
		reserve(count + size);
		System.arraycopy(data, pos, buffer, count, size);
		count += size;
		return this;
	}

	public Octets append(Octets data, int pos, int size) {
		return append(data.buffer, pos, size);
	}

	public Octets append(byte[] data) {
		return append(data, 0, data.length);
	}

	public Octets append(Octets data) {
		return append(data.buffer, 0, data.size());
	}

	@Override
	public int compareTo(Octets rhs) {
		int c = count - rhs.count;
		if (c != 0)
			return c;
		byte[] v1 = buffer;
		byte[] v2 = rhs.buffer;
		for (int i = 0; i < count; i++) {
			int v = v1[i] - v2[i];
			if (v != 0)
				return v;
		}
		return 0;
	}

	@Override
	public boolean equals(Object o) {
		return this == o || (o instanceof Octets && compareTo((Octets) o) == 0);
	}

	@Override
	public int hashCode() {
		int result = 1;
		for (int i = 0; i < count; i++)
			result = 31 * result + buffer[i];
		return result;
	}

	@Override
	public String toString() {
		return "octets.size=" + count;
	}

	public byte[] getBytes() {
		byte[] tmp = new byte[count];
		System.arraycopy(buffer, 0, tmp, 0, count);
		return tmp;
	}

	public byte[] array() {
		return buffer;
	}

	public byte getByte(int pos) {
		return buffer[pos];
	}

	public void setByte(int pos, byte b) {
		buffer[pos] = b;
	}

	public ByteBuffer getByteBuffer(int off, int size) {
		return ByteBuffer.wrap(buffer, off, size);
	}

	public ByteBuffer getByteBuffer(int off) {
		return ByteBuffer.wrap(buffer, off, count - off);
	}

	public ByteBuffer getByteBuffer() {
		return ByteBuffer.wrap(buffer, 0, count);
	}
}
