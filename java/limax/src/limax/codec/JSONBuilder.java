package limax.codec;

import java.util.Set;

public final class JSONBuilder {
	private final StringBuilder sb = new StringBuilder();
	private final Set<Object> l;

	JSONBuilder(Set<Object> l) {
		this.l = l;
	}

	public JSONBuilder append(Object v) {
		try {
			JSONEncoder.encode(l, v, sb);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return this;
	}

	public JSONBuilder append(JSONMarshal v) {
		if (!l.add(v))
			throw new RuntimeException(
					"JSONBuilder loop detected. object = " + v + ", type = " + v.getClass().getName());
		v.marshal(this);
		l.remove(v);
		return this;
	}

	public JSONBuilder begin() {
		sb.append('{');
		return this;
	}

	public JSONBuilder end() {
		int last = sb.length() - 1;
		if (sb.charAt(last) == ',')
			sb.setCharAt(last, '}');
		else
			sb.append('}');
		return this;
	}

	public JSONBuilder comma() {
		sb.append(',');
		return this;
	}

	public JSONBuilder colon() {
		sb.append(':');
		return this;
	}

	@Override
	public String toString() {
		return sb.toString();
	}
}
