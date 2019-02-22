package limax.http;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import limax.util.StringUtils;

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
		headers.put(key, v);
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

	StringBuilder write(StringBuilder sb) {
		headers.forEach((k, v) -> {
			if (k.charAt(0) != ':')
				v.forEach(v0 -> sb.append(StringUtils.upper1(k)).append(": ").append(v0).append("\r\n"));
		});
		return sb;
	}
}
