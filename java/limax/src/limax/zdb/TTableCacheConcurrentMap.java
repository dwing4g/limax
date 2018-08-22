package limax.zdb;

import java.util.Collection;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

class TTableCacheConcurrentMap<K, V> extends TTableCache<K, V> {
	private final ConcurrentMap<K, TRecord<K, V>> map = new ConcurrentHashMap<>();
	private Runnable cleanWorker;
	private boolean cleanning = false;

	@Override
	void initialize(TTable<K, V> table, limax.xmlgen.Table meta) {
		super.initialize(table, meta);
		cleanWorker = () -> {
			if (setCleanning()) {
				TTableCacheConcurrentMap.this.cleanNow();
				resetCleanning();
			}
		};
		int delay = 3600 * 1000;
		int initialDelay = Zdb.random().nextInt(delay);
		Zdb.scheduler().scheduleWithFixedDelay(cleanWorker, initialDelay, delay, TimeUnit.MILLISECONDS);
	}

	synchronized boolean setCleanning() {
		if (this.cleanning)
			return false;
		this.cleanning = true;
		return true;
	}

	synchronized void resetCleanning() {
		this.cleanning = false;
	}

	@Override
	public void clean() {
		this.cleanWorker.run();
	}

	private void cleanNow() {
		int capacity = getCapacity();
		if (capacity <= 0)
			return;
		int size = this.size();
		if (size <= capacity)
			return;
		PriorityQueue<AccessTimeRecord<K, V>> sorted = new PriorityQueue<>();
		for (TRecord<K, V> r : map.values())
			sorted.add(new AccessTimeRecord<K, V>(r));
		for (int nclean = size - capacity + 255; nclean > 0;) {
			AccessTimeRecord<K, V> ar = sorted.poll();
			if (null == ar)
				break;
			if (ar.accessTime != ar.r.getLastAccessTime())
				continue;
			if (tryRemoveRecord(ar.r))
				--nclean;
		}
	}

	private static class AccessTimeRecord<K, V> implements Comparable<AccessTimeRecord<K, V>> {
		long accessTime;
		TRecord<K, V> r;

		@Override
		public int compareTo(AccessTimeRecord<K, V> o) {
			return accessTime < o.accessTime ? -1 : (accessTime == o.accessTime ? 0 : 1);
		}

		AccessTimeRecord(TRecord<K, V> r) {
			this.accessTime = r.getLastAccessTime();
			this.r = r;
		}
	}

	@Override
	public void clear() {
		if (super.getTable().getPersistence() != Table.Persistence.MEMORY)
			throw new UnsupportedOperationException();
		map.clear();
	}

	@Override
	public void walk(Query<K, V> query) {
		_walk(map.values(), query);
	}

	@Override
	int size() {
		return map.size();
	}

	@Override
	TRecord<K, V> get(K k) {
		TRecord<K, V> r = map.get(k);
		if (null != r)
			r.access();
		return r;
	}

	@Override
	void addNoLog(K key, TRecord<K, V> r) {
		if (null != map.putIfAbsent(key, r))
			throw new XError("cache.addNoLog duplicate record");
	}

	@Override
	void add(K key, TRecord<K, V> r) {
		if (null != map.putIfAbsent(key, r))
			throw new XError("cache.add duplicate record");
		logAddRemove(key, r);
	}

	@Override
	TRecord<K, V> remove(K key) {
		return map.remove(key);
	}

	@Override
	Collection<TRecord<K, V>> values() {
		return map.values();
	}
}
