package limax.zdb;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;

final class TStorage<K, V> implements StorageInterface {
	private final TTable<K, V> table;
	private final StorageEngine engine;

	TStorage(TTable<K, V> table, LoggerEngine logger) {
		this.table = table;
		switch (Zdb.meta().getEngineType()) {
		case MYSQL:
			engine = new StorageMysql((LoggerMysql) logger, table.getName());
			break;
		case EDB:
			engine = new StorageEdb((LoggerEdb) logger, table.getName());
			break;
		default:
			throw new XError("unknown engine type");
		}
	}

	@Override
	public StorageEngine getEngine() {
		return engine;
	}

	private final Map<K, TRecord<K, V>> changed = new ConcurrentHashMap<>();
	private Map<K, TRecord<K, V>> marshal = new ConcurrentHashMap<>();
	private Map<K, TRecord<K, V>> snapshot = new ConcurrentHashMap<>();
	private final ReentrantReadWriteLock snapshotLock = new ReentrantReadWriteLock();

	void onRecordChanged(TRecord<K, V> r) {
		Duration.Record duration = Transaction.current().duration();
		if (duration != null) {
			switch (r.state()) {
			case REMOVE:
			case INDB_REMOVE:
				duration.remove(table.getName(), table.marshalKey(r.getKey()));
				break;
			default:
				duration.replace(table.getName(), table.marshalKey(r.getKey()), table.marshalValue(r.getValue()));
			}
		}
		if (r.state() == TRecord.State.REMOVE) {
			changed.remove(r.getKey());
			marshal.remove(r.getKey());
		} else {
			changed.put(r.getKey(), r);
		}
	}

	boolean isClean(K key) {
		return !changed.containsKey(key) && !marshal.containsKey(key);
	}

	private volatile long countMarshalN = 0;
	private volatile long countMarshal0 = 0;
	private volatile long countFlush = 0;
	private volatile long countSnapshot = 0;
	private volatile long countMarshalNTryFail = 0;
	volatile long flushKeySize = 0;
	volatile long flushValueSize = 0;

	@Override
	public long marshalN() {
		long marshaled = 0;
		long tryFail = 0;
		for (Iterator<TRecord<K, V>> it = changed.values().iterator(); it.hasNext();) {
			TRecord<K, V> r = it.next();
			if (r.tryMarshalN(() -> {
				marshal.put(r.getKey(), r);
				it.remove();
			}))
				++marshaled;
			else
				++tryFail;
		}
		countMarshalN += marshaled;
		countMarshalNTryFail += tryFail;
		return marshaled;
	}

	@Override
	public long marshal0() {
		marshal.putAll(changed);
		long count = changed.values().stream().peek(TRecord::marshal0).count();
		changed.clear();
		countMarshal0 += count;
		return count;
	}

	@Override
	public long snapshot() {
		Map<K, TRecord<K, V>> tmp = snapshot;
		snapshot = marshal;
		marshal = tmp;
		long count = snapshot.values().stream().peek(TRecord::snapshot).count();
		countSnapshot += count;
		return count;
	}

	@Override
	public long flush0() {
		long count = snapshot.values().stream().filter(r -> r.flush(this)).count();
		countFlush += count;
		return count;
	}

	@Override
	public void cleanup() {
		Map<K, TRecord<K, V>> tmp;
		Lock lock = snapshotLock.writeLock();
		lock.lock();
		try {
			tmp = snapshot;
			snapshot = new ConcurrentHashMap<>();
		} finally {
			lock.unlock();
		}
		tmp.values().forEach(TRecord::clear);
	}

	final boolean exist(K key, TTable<K, V> table) {
		Lock lock = snapshotLock.readLock();
		lock.lock();
		try {
			TRecord<K, V> r = snapshot.get(key);
			if (r != null)
				return r.exist();
		} finally {
			lock.unlock();
		}
		return engine.exist(table.marshalKey(key));
	}

	V find(K key, TTable<K, V> table) {
		OctetsStream value;
		Lock lock = snapshotLock.readLock();
		lock.lock();
		try {
			TRecord<K, V> r = snapshot.get(key);
			if (r != null) {
				value = r.find();
				if (value == null)
					return null;
			} else {
				value = null;
			}
		} finally {
			lock.unlock();
		}
		try {
			if (value == null) {
				Octets _value = engine.find(table.marshalKey(key));
				if (_value == null)
					return null;
				value = OctetsStream.wrap(_value);
			}
			return table.unmarshalValue(value);
		} catch (MarshalException e) {
			throw new RuntimeException(e);
		}
	}

	public long getCountFlush() {
		return countFlush;
	}

	public long getCountMarshal0() {
		return countMarshal0;
	}

	public long getCountMarshalN() {
		return countMarshalN;
	}

	public long getCountMarshalNTryFail() {
		return countMarshalNTryFail;
	}

	public long getCountSnapshot() {
		return countSnapshot;
	}

	public long getFlushKeySize() {
		return flushKeySize;
	}

	public long getFlushValueSize() {
		return flushValueSize;
	}
}
