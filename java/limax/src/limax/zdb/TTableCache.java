package limax.zdb;

import java.util.Collection;

import limax.zdb.TRecord.State;

public abstract class TTableCache<K, V> {

	public interface Query<K, V> {
		void onQuery(K key, V value);
	}

	/**
	 * 
	 * On memory table callback it while threshold reached,
	 * 
	 * Other table callback never.
	 * 
	 * 
	 * @param <K>
	 *            key
	 * @param <V>
	 *            value
	 */
	public interface RemoveHandle<K, V> {
		void onRecordRemoved(K key, V value);
	}

	private volatile TTable<K, V> table;
	private volatile RemoveHandle<K, V> removedhandle;
	private volatile int capacity;

	TTableCache() {
	}

	void initialize(TTable<K, V> table, limax.xmlgen.Table meta) {
		this.table = table;
		this.capacity = meta.getCacheCapValue();
	}

	static <K, V> TTableCache<K, V> newInstance(TTable<K, V> table, limax.xmlgen.Table meta) {
		try {
			@SuppressWarnings("unchecked")
			TTableCache<K, V> cache = (TTableCache<K, V>) Class.forName(Zdb.meta().getDefaultTableCache())
					.getDeclaredConstructor().newInstance();
			cache.initialize(table, meta);
			return cache;
		} catch (Throwable e) {
			throw new XError(e);
		}
	}

	/**
	 * Removes all of the mappings from this map. The map will be empty after
	 * this call returns. clear all record, even if dirty
	 * 
	 * @throws UnsupportedOperationException
	 *             not memory table
	 */
	public abstract void clear();

	/**
	 * cleanup memory, dirty record not clear
	 */
	public abstract void clean();

	/**
	 * @see Query
	 * @param query
	 *            the callback
	 */
	public abstract void walk(Query<K, V> query);

	abstract Collection<TRecord<K, V>> values();

	abstract int size();

	public final TTable<K, V> getTable() {
		return table;
	}

	public final int getCapacity() {
		return capacity;
	}

	public void setCapacity(int capacity) {
		this.capacity = capacity;
	}

	/**
	 * 
	 * @see RemoveHandle
	 * @param handle
	 *            the callback
	 */
	public void setRemovedhandle(RemoveHandle<K, V> handle) {
		this.removedhandle = handle;
	}

	abstract TRecord<K, V> get(K k);

	abstract void addNoLog(K key, TRecord<K, V> r);

	abstract void add(K key, TRecord<K, V> r);

	abstract TRecord<K, V> remove(K k);

	final void _walk(Collection<TRecord<K, V>> records, Query<K, V> query) {
		records.forEach(r -> {
			Lockey lock = r.getLockey();
			lock.rLock();
			try {
				V value = r.getValue();
				if (null != value)
					query.onQuery(r.getKey(), value);
			} finally {
				lock.rUnlock();
			}
		});
	}

	final void logAddRemove(K key, TRecord<K, V> r) {
		Transaction.currentSavepoint().add(r.getLogKey(), new Log() {
			private final State saved_state = r.state();

			@Override
			public void commit() {
				table.onRecordChanged(r, true, saved_state);
			}

			@Override
			public void rollback() {
				remove(key);
			}
		});
	}

	final boolean tryRemoveRecord(TRecord<K, V> r) {
		Lockey lockey = r.getLockey();
		if (!lockey.wTryLock())
			return false;
		try {
			TStorage<K, V> storage = table.getStorage();
			K key = r.getKey();
			if (storage == null) {
				remove(key);
				if (removedhandle != null)
					Zdb.executor().execute(() -> removedhandle.onRecordRemoved(key, r.getValue()));
				return true;
			}
			if (storage.isClean(key)) {
				remove(key);
				return true;
			}
			return false;
		} finally {
			lockey.wUnlock();
		}
	}
}
