package limax.zdb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

final class TTableCacheLRU<K, V> extends TTableCache<K, V> {
	private ReentrantLock lock;
	private LinkedHashMap<K, TRecord<K, V>> lru;

	@Override
	void initialize(TTable<K, V> table, limax.xmlgen.Table meta) {
		super.initialize(table, meta);
		lock = new ReentrantLock();
		lru = new LinkedHashMap<K, TRecord<K, V>>(16, 0.75f, true) {
			private static final long serialVersionUID = 650878930636563676L;

			@Override
			protected boolean removeEldestEntry(Map.Entry<K, TRecord<K, V>> eldest) {
				int capacity = getCapacity();
				if (capacity > 0 && capacity < size())
					cleaner.start();
				return false;
			}
		};
	}

	private final Cleaner cleaner = new Cleaner();

	private final class Cleaner implements Runnable {
		private final AtomicBoolean working = new AtomicBoolean();

		public void start() {
			if (working.compareAndSet(false, true))
				Zdb.executor().execute(this);
		}

		@Override
		public void run() {
			Lock readLock = Zdb.tables().flushReadLock();
			readLock.lock();
			try {
				Collection<TRecord<K, V>> eldests = new ArrayList<>();
				lock.lock();
				try {
					int capacity = getCapacity();
					int size = lru.size();
					if (size > capacity) {
						int nclean = Math.min(size - capacity + 255, size);
						Iterator<TRecord<K, V>> it = lru.values().iterator();
						for (int i = 0; i < nclean; ++i)
							eldests.add(it.next());
					}
				} finally {
					lock.unlock();
				}
				eldests.forEach(r -> tryRemoveRecord(r));
			} finally {
				readLock.unlock();
				working.set(false);
			}
		}
	}

	@Override
	public void clear() {
		if (getTable().getPersistence() != Table.Persistence.MEMORY)
			throw new UnsupportedOperationException();
		lock.lock();
		try {
			lru.clear();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void clean() {
	}

	@Override
	int size() {
		lock.lock();
		try {
			return lru.size();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void walk(Query<K, V> query) {
		Collection<TRecord<K, V>> records;
		lock.lock();
		try {
			records = new ArrayList<>(lru.values());
		} finally {
			lock.unlock();
		}
		_walk(records, query);
	}

	@Override
	TRecord<K, V> get(K key) {
		lock.lock();
		try {
			return lru.get(key);
		} finally {
			lock.unlock();
		}
	}

	@Override
	void addNoLog(K key, TRecord<K, V> r) {
		lock.lock();
		try {
			if (lru.containsKey(key))
				throw new XError("cache.addNoLog duplicate record");
			lru.put(key, r);
		} finally {
			lock.unlock();
		}
	}

	@Override
	void add(K key, TRecord<K, V> r) {
		lock.lock();
		try {
			if (lru.containsKey(key))
				throw new XError("cache.add duplicate record");
			logAddRemove(key, r);
			lru.put(key, r);
		} finally {
			lock.unlock();
		}
	}

	@Override
	TRecord<K, V> remove(K key) {
		lock.lock();
		try {
			return lru.remove(key);
		} finally {
			lock.unlock();
		}
	}

	@Override
	Collection<TRecord<K, V>> values() {
		return lru.values();
	}
}
