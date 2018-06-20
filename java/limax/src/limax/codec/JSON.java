package limax.codec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JSON {
	final Object data;

	static final class Null extends Object {
		@Override
		public String toString() {
			return "null";
		}
	}

	static final Object Null = new Null();

	static final class True extends Object {
		@Override
		public String toString() {
			return "true";
		}
	}

	static final Object True = new True();

	static final class False extends Object {
		@Override
		public String toString() {
			return "false";
		}
	};

	static final Object False = new False();

	JSON(Object data) {
		this.data = data;
	}

	@SuppressWarnings("unchecked")
	public JSON get(String key) throws JSONException {
		try {
			return new JSON(((Map<String, Object>) data).get(key));
		} catch (Throwable t) {
			throw new JSONException(t);
		}
	}

	@SuppressWarnings("unchecked")
	public Set<String> keySet() throws JSONException {
		try {
			return ((Map<String, Object>) data).keySet();
		} catch (Throwable t) {
			throw new JSONException(t);
		}
	}

	@SuppressWarnings("unchecked")
	public JSON get(int index) throws JSONException {
		try {
			return new JSON(
					index < 0 || index >= ((List<Object>) data).size() ? null : ((List<Object>) data).get(index));
		} catch (Throwable t) {
			throw new JSONException(t);
		}
	}

	@SuppressWarnings("unchecked")
	public JSON[] toArray() throws JSONException {
		try {
			List<JSON> list = new ArrayList<JSON>();
			for (Object o : (List<Object>) data)
				list.add(new JSON(o));
			return list.toArray(new JSON[0]);
		} catch (Throwable t) {
			throw new JSONException(t);
		}
	}

	@Override
	public String toString() {
		return data == null ? "undefined" : data.toString();
	}

	public boolean booleanValue() {
		if (data == True)
			return true;
		if (data == False || data == Null || data == null)
			return false;
		if (data instanceof Long)
			return ((Long) data).longValue() != 0L;
		if (data instanceof Double)
			return ((Double) data).doubleValue() != 0.0;
		if (data instanceof String)
			return !((String) data).isEmpty();
		return true;
	}

	public int intValue() throws JSONException {
		if (data == True)
			return 1;
		if (data == False || data == Null)
			return 0;
		if (data instanceof Long)
			return ((Long) data).intValue();
		if (data instanceof Double)
			return ((Double) data).intValue();
		if (data instanceof String)
			try {
				return Integer.parseInt((String) data);
			} catch (NumberFormatException e) {
			}
		throw new JSONException(data + " cast to int");
	}

	public long longValue() throws JSONException {
		if (data == True)
			return 1L;
		if (data == False || data == Null)
			return 0L;
		if (data instanceof Long)
			return ((Long) data).longValue();
		if (data instanceof Double)
			return ((Double) data).longValue();
		if (data instanceof String)
			try {
				return Long.parseLong((String) data);
			} catch (NumberFormatException e) {
			}
		throw new JSONException(data + " cast to long");
	}

	public double doubleValue() throws JSONException {
		if (data == True)
			return 1.0;
		if (data == False || data == Null)
			return 0.0;
		if (data instanceof Long)
			return ((Long) data).doubleValue();
		if (data instanceof Double)
			return ((Double) data).doubleValue();
		if (data instanceof String)
			try {
				return Double.parseDouble((String) data);
			} catch (NumberFormatException e) {
			}
		throw new JSONException(data + " cast to double");
	}

	public boolean isUndefined() {
		return data == null;
	}

	public boolean isNull() {
		return data == Null;
	}

	public boolean isBoolean() {
		return data == True || data == False;
	}

	public boolean isString() {
		return data instanceof String;
	}

	public boolean isNumber() {
		return data instanceof Long || data instanceof Double;
	}

	public boolean isObject() {
		return data instanceof Map;
	}

	public boolean isArray() {
		return data instanceof List;
	}

	public static JSON parse(String text) throws JSONException {
		try {
			JSONDecoder decoder = new JSONDecoder();
			for (int i = 0, l = text.length(); i < l; i++)
				decoder.accept(text.charAt(i));
			decoder.flush();
			return decoder.get();
		} catch (Exception e) {
			throw new JSONException(e);
		}
	}

	public static String stringify(Object obj) throws JSONException {
		StringBuilder sb = new StringBuilder();
		JSONEncoder.encode(obj, sb);
		return sb.toString();
	}
}
