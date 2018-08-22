package limax.zdb;

import java.util.Collection;
import java.util.Collections;
import java.util.WeakHashMap;

class TTableCacheNull<K, V> extends TTableCache<K, V> {
	private final WeakHashMap<K, TRecord<K, V>> weakmap = new WeakHashMap<K, TRecord<K, V>>(16, 0.75f);

	@Override
	void initialize(TTable<K, V> table, limax.xmlgen.Table meta) {
		super.initialize(table, meta);
	}

	@Override
	public void clear() {
		weakmap.clear();
	}

	@Override
	public void clean() {
	}

	@Override
	public void walk(Query<K, V> query) {
	}

	@Override
	int size() {
		return weakmap.size();
	}

	@Override
	TRecord<K, V> get(K k) {
		return weakmap.get(k);
	}

	@Override
	void addNoLog(K key, TRecord<K, V> r) {
		weakmap.put(key, r);
	}

	@Override
	void add(K key, TRecord<K, V> r) {
		weakmap.put(key, r);
		logAddRemove(key, r);
	}

	@Override
	TRecord<K, V> remove(K k) {
		return weakmap.remove(k);
	}

	@Override
	Collection<TRecord<K, V>> values() {
		return Collections.emptyList();
	}
}
