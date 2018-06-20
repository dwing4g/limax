package limax.endpoint.script;

import java.util.Collection;

public interface DictionaryCache {
	void put(String key, String value);

	String get(String key);

	Collection<String> keys();
}
