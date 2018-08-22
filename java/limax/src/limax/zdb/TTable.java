package limax.zdb;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.util.Helper;
import limax.util.MBeans;
import limax.util.Resource;
import limax.util.Trace;
import limax.zdb.TRecord.State;

public abstract class TTable<K, V> extends AbstractTable implements TTableMBean {
	private String lockname;
	private int lockId;
	private limax.xmlgen.Table meta;
	private Path preload;
	private AutoKeys.AutoKey autoKey;
	private Resource mbean;

	protected TTable() {
	}

	protected final Long nextKey() {
		return autoKey.next();
	}

	void recoverKey(long key) {
		autoKey.recover(key);
	}

	protected final boolean add(K key, V value) {
		Objects.requireNonNull(key, "key is null");
		Objects.requireNonNull(value, "value is null");
		Lockey lockey = Lockeys.getLockey(lockId, key);
		Transaction.current().wAddLockey(lockey);
		if (null != autoKey)
			autoKey.accept((Long) key);
		countAdd.incrementAndGet();
		TRecord<K, V> r = cache.get(key);
		if (null != r)
			return r.add(value);
		countAddMiss.incrementAndGet();
		if (_exist(key)) {
			countAddStorageMiss.incrementAndGet();
			return false;
		}
		cache.add(key, new TRecord<K, V>(this, value, lockey, TRecord.State.ADD));
		return true;
	}

	protected final boolean remove(K key) {
		Objects.requireNonNull(key, "key is null");
		Transaction current = Transaction.current();
		Lockey lockey = Lockeys.getLockey(lockId, key);
		current.wAddLockey(lockey);
		current.removeCachedTRecord(this, key);
		countRemove.incrementAndGet();
		TRecord<K, V> r = cache.get(key);
		if (null != r)
			return r.remove();
		countRemoveMiss.incrementAndGet();
		boolean exists = _exist(key);
		if (!exists)
			countRemoveStorageMiss.incrementAndGet();
		cache.add(key, new TRecord<K, V>(this, null, lockey, exists ? State.INDB_REMOVE : State.REMOVE));
		return exists;
	}

	protected V get(K key, boolean wlock) {
		Objects.requireNonNull(key, "key is null");
		Transaction current = Transaction.current();
		Lockey lockey = Lockeys.getLockey(lockId, key);
		if (wlock)
			current.wAddLockey(lockey);
		else
			current.rAddLockey(lockey);
		countGet.incrementAndGet();
		TRecord<K, V> rCached = current.getCachedTRecord(this, key);
		if (rCached != null)
			return rCached.getValue();
		Lockey cacheLockey = Lockeys.getLockey(-lockId, key);
		cacheLockey.wLock();
		try {
			TRecord<K, V> r = cache.get(key);
			if (null == r) {
				countGetMiss.incrementAndGet();
				V value = _find(key);
				if (null == value) {
					countGetStorageMiss.incrementAndGet();
					return null;
				}
				r = new TRecord<K, V>(this, value, lockey, TRecord.State.INDB_GET);
				cache.addNoLog(key, r);
			}
			current.addCachedTRecord(this, r);
			return r.getValue();
		} finally {
			cacheLockey.wUnlock();
		}
	}

	private final ThreadLocal<LogRecord<K, V>> logRecord = ThreadLocal.withInitial(() -> new LogRecord<>(this));
	private final ListenerMap listenerMap = new ListenerMap();

	public final Runnable addListener(Listener l, String name) {
		return listenerMap.add(name, l);
	}

	public final Runnable addListener(Listener l) {
		return addListener(l, "");
	}

	public final boolean hasListener() {
		return listenerMap.hasListener();
	}

	final void logNotify() {
		try {
			logRecord.get().logNotify(listenerMap);
		} catch (Throwable e) {
			logRecord.remove();
			Trace.fatal("TTable.logNotify", e);
		}
	}

	private TTableCache<K, V> cache;

	public final TTableCache<K, V> getCache() {
		return cache;
	}

	private TStorage<K, V> storage;

	final TStorage<K, V> getStorage() {
		return storage;
	}

	private void onRecordChanged(TRecord<K, V> r) {
		if (r.state() == TRecord.State.REMOVE)
			cache.remove(r.getKey());
		if (storage != null)
			storage.onRecordChanged(r);
	}

	final void onRecordChanged(TRecord<K, V> r, LogNotify ln) {
		logRecord.get().onChanged(r, ln);
		Transaction.current().addLastCommitActions(() -> onRecordChanged(r));
	}

	final void onRecordChanged(TRecord<K, V> r, boolean cc, State ss) {
		logRecord.get().onChanged(r, cc, ss);
		Transaction.current().addLastCommitActions(() -> onRecordChanged(r));
	}

	@Override
	final StorageInterface open(limax.xmlgen.Table meta, LoggerEngine logger) {
		if (null != storage)
			throw new XError("table has opened : " + getName());
		this.meta = meta;
		this.lockname = meta.getLock().isEmpty() ? getName() : meta.getLock();
		this.cache = TTableCache.newInstance(this, meta);
		this.storage = meta.isMemory() ? null : new TStorage<K, V>(this, logger);
		this.autoKey = meta.isAutoIncrement() ? Zdb.tables().getTableSys().getAutoKeys().getAutoKey(getName()) : null;
		this.mbean = MBeans.register(Zdb.mbeans(), this, "limax.zdb:type=Tables,name=" + getName());
		ManagementFactory.setTTableMBean(getName(), this);
		return storage;
	}

	void preload(Path dir) {
		if (dir == null)
			return;
		try {
			for (OctetsStream os = OctetsStream
					.wrap(Octets.wrap(Files.readAllBytes(preload = dir.resolve(getName())))); !os.eos();) {
				K key = unmarshalKey(os);
				V value = unmarshalValue(os);
				Lockey lockey = Lockeys.getLockey(getLockId(), key);
				cache.addNoLog(key, new TRecord<K, V>(this, value, lockey, TRecord.State.INDB_GET));
			}
		} catch (Exception e) {
		}
	}

	public limax.xmlgen.Table meta() {
		return meta;
	}

	@Override
	final void close() {
		if (storage != null)
			storage = null;
		if (mbean != null)
			mbean.close();
		if (preload == null)
			return;
		try (OutputStream os = new BufferedOutputStream(new FileOutputStream(preload.toFile()))) {
			for (TRecord<K, V> r : cache.values()) {
				os.write(marshalKey(r.getKey()).getBytes());
				os.write(marshalValue(r.getValue()).getBytes());
			}
		} catch (Exception e) {
		}
	}

	protected abstract V newValue();

	protected abstract OctetsStream marshalKey(K key);

	protected abstract OctetsStream marshalValue(V value);

	protected abstract K unmarshalKey(OctetsStream os) throws MarshalException;

	protected abstract V unmarshalValue(OctetsStream os) throws MarshalException;

	private final boolean _exist(K key) {
		return storage != null ? storage.exist(key, this) : false;
	}

	private final V _find(K key) {
		return storage != null ? storage.find(key, this) : null;
	}

	public interface IWalk<K, V> {
		boolean onRecord(K k, V v);
	}

	public final void walk(IWalk<K, V> iwalk) {
		Objects.requireNonNull(iwalk, "iwalk is null");
		if (null == storage)
			throw new RuntimeException("walk on memory table " + meta.getName() + ", use TTableCache.walk instead.");
		storage.getEngine().walk((key, data) -> {
			try {
				K k = unmarshalKey(OctetsStream.wrap(Octets.wrap(key)));
				V v = unmarshalValue(OctetsStream.wrap(Octets.wrap(data)));
				return iwalk.onRecord(k, v);
			} catch (Throwable e) {
				if (Trace.isErrorEnabled())
					Trace.error("table:" + getName() + ",walk:" + iwalk.getClass().getName() + ",key:"
							+ Helper.toHexString(key) + ",value:" + Helper.toHexString(data) + ",error:", e);
				return true;
			}
		});
	}

	private final AtomicLong countAdd = new AtomicLong();
	private final AtomicLong countAddMiss = new AtomicLong();
	private final AtomicLong countAddStorageMiss = new AtomicLong();

	private final AtomicLong countGet = new AtomicLong();
	private final AtomicLong countGetMiss = new AtomicLong();
	private final AtomicLong countGetStorageMiss = new AtomicLong();

	private final AtomicLong countRemove = new AtomicLong();
	private final AtomicLong countRemoveMiss = new AtomicLong();
	private final AtomicLong countRemoveStorageMiss = new AtomicLong();

	@Override
	public String getLockName() {
		return lockname;
	}

	int getLockId() {
		return lockId;
	}

	void setLockId(int lockId) {
		this.lockId = lockId;
	}

	@Override
	public Persistence getPersistence() {
		return meta.isMemory() ? Persistence.MEMORY : Persistence.DB;
	}

	@Override
	public String getPersistenceName() {
		return getPersistence().name();
	}

	@Override
	public void setCacheCapacity(int capacity) {
		cache.setCapacity(capacity);
	}

	@Override
	public int getCacheCapacity() {
		return cache.getCapacity();
	}

	@Override
	public String getCacheClassName() {
		return cache.getClass().getName();
	}

	@Override
	public int getCacheSize() {
		return cache.size();
	}

	@Override
	public long getCountAdd() {
		return countAdd.get();
	}

	@Override
	public long getCountAddMiss() {
		return countAddMiss.get();
	}

	@Override
	public long getCountAddStorageMiss() {
		return countAddStorageMiss.get();
	}

	@Override
	public long getCountGet() {
		return countGet.get();
	}

	@Override
	public long getCountGetMiss() {
		return countGetMiss.get();
	}

	@Override
	public long getCountGetStorageMiss() {
		return countGetStorageMiss.get();
	}

	@Override
	public long getCountRemove() {
		return countRemove.get();
	}

	@Override
	public long getCountRemoveMiss() {
		return countRemoveMiss.get();
	}

	@Override
	public long getCountRemoveStorageMiss() {
		return countRemoveStorageMiss.get();
	}

	private String format(long miss, long ops) {
		return String.format("%.2f", (double) (ops - miss) / ops);
	}

	@Override
	public String getPercentAddHit() {
		return format(getCountAddMiss(), getCountAdd());
	}

	@Override
	public String getPercentGetHit() {
		return format(getCountGetMiss(), getCountGet());
	}

	@Override
	public String getPercentRemoveHit() {
		return format(getCountRemoveMiss(), getCountRemove());
	}

	@Override
	public String getPercentCacheHit() {
		return format(getCountAddMiss() + getCountRemoveMiss() + getCountGetMiss(),
				getCountAdd() + getCountRemove() + getCountGet());
	}

	@Override
	public long getStorageCountFlush() {
		return null != storage ? storage.getCountFlush() : -1;
	}

	@Override
	public long getStorageCountMarshal0() {
		return null != storage ? storage.getCountMarshal0() : -1;
	}

	@Override
	public long getStorageCountMarshalN() {
		return null != storage ? storage.getCountMarshalN() : -1;
	}

	@Override
	public long getStorageCountMarshalNTryFail() {
		return null != storage ? storage.getCountMarshalNTryFail() : -1;
	}

	@Override
	public long getStorageCountSnapshot() {
		return null != storage ? storage.getCountSnapshot() : -1;
	}

	@Override
	public long getStorageFlushKeySize() {
		return null != storage ? storage.flushKeySize : -1;
	}

	@Override
	public long getStorageFlushValueSize() {
		return null != storage ? storage.flushValueSize : -1;
	}
}
