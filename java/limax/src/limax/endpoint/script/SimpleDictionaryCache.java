package limax.endpoint.script;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class SimpleDictionaryCache implements DictionaryCache {
	private final Map<String, String> map = new HashMap<String, String>();

	@Override
	public void put(String key, String value) {
		synchronized (map) {
			map.put(key, value);
		}
	}

	@Override
	public String get(String key) {
		synchronized (map) {
			return map.get(key);
		}
	}

	@Override
	public Collection<String> keys() {
		synchronized (map) {
			return map.keySet();
		}
	}
}
