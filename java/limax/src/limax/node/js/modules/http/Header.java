package limax.node.js.modules.http;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import limax.codec.Codec;
import limax.codec.CodecException;
import limax.codec.Pump;
import limax.codec.SinkStream;
import limax.node.js.Buffer;

public class Header implements Pump {
	private final static byte[] CRLF = new byte[] { 13, 10 };
	private final static Charset DEFAULT_CHARSETS = StandardCharsets.ISO_8859_1;
	private final static Set<String> single = new HashSet<>();
	static {
		single.add("age");
		single.add("authorization");
		single.add("content-length");
		single.add("content-type");
		single.add("etag");
		single.add("expires");
		single.add("from");
		single.add("host");
		single.add("if-modified-since");
		single.add("if-unmodified-since");
		single.add("last-modified");
		single.add("location");
		single.add("max-forwards");
		single.add("proxy-authorization");
		single.add("referer");
		single.add("retry-after");
		single.add("user-agent");
	}

	private class Field implements Pump {
		private final String name;
		private final List<String> values = new ArrayList<>();

		Field(String name) {
			this.name = name;
		}

		public Field setValue(String v) {
			if (single.contains(name.toLowerCase()))
				values.clear();
			values.add(v);
			return this;
		}

		@Override
		public void render(Codec out) throws CodecException {
			byte[] data = (name + ": " + String.join(", ", values)).getBytes(DEFAULT_CHARSETS);
			out.update(data, 0, data.length);
			out.update(CRLF, 0, CRLF.length);
		}
	}

	private final Map<String, Field> fields = new TreeMap<>();

	public void clear() {
		fields.clear();
	}

	public void setIfAbsent(String name, String value) {
		fields.putIfAbsent(name.toLowerCase(), new Field(name).setValue(value));
	}

	public void set(String name, String value) {
		fields.computeIfAbsent(name.toLowerCase(), k -> new Field(name)).setValue(value);
	}

	public String get(String name) {
		Field field = fields.get(name.toLowerCase());
		return field == null ? null : String.join(", ", field.values);
	}

	public Object[] getArray(String name) {
		Field field = fields.get(name.toLowerCase());
		return field == null ? new String[0] : field.values.toArray();
	}

	public void remove(String name) {
		fields.remove(name.toLowerCase());
	}

	public Map<String, String> get() {
		Map<String, String> map = new HashMap<>();
		for (Map.Entry<String, Field> entry : fields.entrySet())
			map.put(entry.getKey(), String.join(", ", entry.getValue().values));
		return map;
	}

	public String[] getRaw() {
		List<String> list = new ArrayList<>();
		for (Field field : fields.values()) {
			for (String value : field.values) {
				list.add(field.name);
				list.add(value);
			}
		}
		return list.toArray(new String[0]);
	}

	public void updateDate() {
		remove("Date");
		set("Date", ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME));
	}

	public Buffer format(String prefix) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos.write(prefix.getBytes(StandardCharsets.ISO_8859_1));
		render(new SinkStream(baos));
		return new Buffer(baos.toByteArray());
	}

	public boolean isEmpty() {
		return fields.isEmpty();
	}

	@Override
	public void render(Codec out) throws CodecException {
		for (Field field : fields.values())
			field.render(out);
		out.update(CRLF, 0, CRLF.length);
	}
}
