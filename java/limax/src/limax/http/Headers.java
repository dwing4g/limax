package limax.http;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Headers {
	private final Map<String, List<String>> headers = new LinkedHashMap<>();

	Headers() {
	}

	public void add(String key, Object value) {
		headers.computeIfAbsent(key.toLowerCase(), k -> new ArrayList<>()).add(value.toString().trim());
	}

	public void set(String key, Object value) {
		List<String> v = new ArrayList<>();
		v.add(value.toString().trim());
		headers.put(key.toLowerCase(), v);
	}

	public String getFirst(String key) {
		List<String> v = headers.get(key.toLowerCase());
		return v == null ? null : v.get(0);
	}

	public List<String> get(String key) {
		return headers.get(key.toLowerCase());
	}

	public Set<Map.Entry<String, List<String>>> entrySet() {
		return headers.entrySet();
	}

	public List<String> remove(String key) {
		return headers.remove(key);
	}

	private static String upper(String s) {
		char[] c = s.toCharArray();
		boolean upper = true;
		for (int i = 0; i < c.length; i++) {
			if (upper)
				c[i] = Character.toUpperCase(c[i]);
			upper = c[i] == '-';
		}
		return new String(c);
	}

	StringBuilder write(StringBuilder sb) {
		headers.forEach((k, v) -> {
			if (k.charAt(0) != ':')
				v.forEach(v0 -> sb.append(upper(k)).append(": ").append(v0).append("\r\n"));
		});
		return sb;
	}

	Headers copy() {
		Headers r = new Headers();
		headers.forEach((k, v) -> r.headers.put(k, v));
		return r;
	}
}
