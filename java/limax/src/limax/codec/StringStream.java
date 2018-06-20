package limax.codec;

import java.util.Map;

public class StringStream {
	private final Map<String, Integer> dictionary;
	private final StringBuilder sb;

	public static String pack(long value) {
		return Long.toString(value, Character.MAX_RADIX);
	}

	public static String pack(int value) {
		return Integer.toString(value, Character.MAX_RADIX);
	}

	public StringStream(Map<String, Integer> dictionary) {
		this.dictionary = dictionary;
		this.sb = new StringBuilder();
	}

	public StringStream(Map<String, Integer> dictionary, String prefix) {
		this.dictionary = dictionary;
		this.sb = new StringBuilder(prefix);
	}

	public StringStream marshal(byte value) {
		return marshal((int) value);
	}

	public StringStream marshal(short value) {
		return marshal((int) value);
	}

	public StringStream marshal(int value) {
		sb.append("I").append(pack(value)).append(":");
		return this;
	}

	public StringStream marshal(long value) {
		if (value >= 0 && value <= 0x1FFFFFFFFFFFFFL || value < 0 && -value <= 0x1FFFFFFFFFFFFFL)
			sb.append("I").append(pack(value)).append(":");
		else
			sb.append("J").append(value).append(":");
		return this;
	}

	public StringStream marshal(float value) {
		sb.append("F").append(value).append(":");
		return this;
	}

	public StringStream marshal(double value) {
		sb.append("F").append(value).append(":");
		return this;
	}

	public StringStream marshal(boolean value) {
		sb.append("B").append(value ? "T" : "F");
		return this;
	}

	public StringStream marshal(String value) {
		sb.append("S").append(pack(value.length())).append(":").append(value);
		return this;
	}

	public StringStream marshal(byte[] value) {
		sb.append("O").append(pack(value.length)).append(":");
		for (byte b : value)
			sb.append(String.format("%02x", b));
		return this;
	}

	public StringStream marshal(Octets value) {
		int j = value.size();
		sb.append("O").append(pack(j)).append(":");
		byte[] bb = value.array();
		for (int i = 0; i < j; i++)
			sb.append(String.format("%02x", bb[i]));
		return this;
	}

	public StringStream marshal(StringMarshal value) {
		return value.marshal(this);
	}

	public StringStream append(String value) {
		sb.append(value);
		return this;
	}

	public StringStream append(char value) {
		sb.append(value);
		return this;
	}

	public StringStream variable(String varname) {
		return append("?").append(pack(dictionary.get(varname))).append("?");
	}

	public int length() {
		return sb.length();
	}

	@Override
	public String toString() {
		return sb.toString();
	}

	public String toString(String postfix) {
		return sb.append(postfix).toString();
	}

	public static StringStream create() {
		return new StringStream(null);
	};

	public static StringStream create(String prefix) {
		return new StringStream(null, prefix);
	};
}
