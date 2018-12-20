package limax.zdb;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

import limax.util.Trace;

public final class Transaction {
	public enum Isolation {
		LEVEL2, LEVEL3
	};

	private final List<Savepoint> logs = new ArrayList<>();

	final Map<LogKey, Object> wrappers = new HashMap<>();

	enum LockeyHolderType {
		WRITE, READ, NONE
	}

	private static class LockeyHolder implements Comparable<LockeyHolder> {
		final Lockey lockey;
		LockeyHolderType type;

		LockeyHolder(Lockey lockey, LockeyHolderType type) {
			this.lockey = lockey;
			this.type = type;
		}

		void cleanup() {
			if (type == LockeyHolderType.WRITE)
				lockey.wUnlock();
			else
				lockey.rUnlock();
		}

		@Override
		public int compareTo(LockeyHolder o) {
			int c = lockey.compareTo(o.lockey);
			return c != 0 ? c : type.ordinal() - o.type.ordinal();
		}
	}

	private final Map<Lockey, LockeyHolder> locks = new HashMap<>();
	private Map<String, TTable<?, ?>> logNotifyTTables = new HashMap<>();
	private final Map<String, Map<Object, Object>> cachedTRecord = new HashMap<>();
	private final List<Runnable> last_commit_actions = new ArrayList<>();
	private final Duration.Record duration = Zdb.allocDurationRecord();

	private Transaction() {
	}

	Duration.Record duration() {
		return duration;
	}

	LockeyHolderType getLockeyHolderType(Lockey lockey) {
		LockeyHolder holder = locks.get(lockey);
		return holder != null ? holder.type : LockeyHolderType.NONE;
	}

	void rAddLockey(Lockey lockey) {
		if (locks.containsKey(lockey))
			return;
		lockey.rLock();
		locks.put(lockey, new LockeyHolder(lockey, LockeyHolderType.READ));
	}

	void wAddLockey(Lockey lockey) {
		LockeyHolder holder = locks.get(lockey);
		if (holder == null) {
			lockey.wLock();
			locks.put(lockey, new LockeyHolder(lockey, LockeyHolderType.WRITE));
		} else if (holder.type == LockeyHolderType.READ) {
			holder.lockey.rUnlock();
			try {
				holder.lockey.wLock();
			} catch (XDeadlock e) {
				locks.remove(lockey);
				throw e;
			}
			holder.type = LockeyHolderType.WRITE;
		}
	}

	void recordLogNotifyTTable(TTable<?, ?> ttable) {
		logNotifyTTables.put(ttable.getName(), ttable);
	}

	<K, V> void addCachedTRecord(TTable<K, V> ttable, TRecord<K, V> r) {
		cachedTRecord.computeIfAbsent(ttable.getName(), v -> new HashMap<>()).put(r.getKey(), r);
	}

	<K, V> void removeCachedTRecord(TTable<K, V> ttable, K k) {
		Map<Object, Object> table = cachedTRecord.get(ttable.getName());
		if (table != null)
			table.remove(k);
	}

	@SuppressWarnings("unchecked")
	<K, V> TRecord<K, V> getCachedTRecord(TTable<K, V> ttable, K k) {
		Map<Object, Object> table = cachedTRecord.get(ttable.getName());
		return table != null ? (TRecord<K, V>) table.get(k) : null;
	}

	void addLastCommitActions(Runnable r) {
		last_commit_actions.add(r);
	}

	Lockey get(Lockey lockey) {
		LockeyHolder holder = locks.get(lockey);
		return holder != null ? holder.lockey : null;
	}

	private void finish() {
		wrappers.clear();
		locks.values().forEach(LockeyHolder::cleanup);
		locks.clear();
		cachedTRecord.clear();
	}

	private int _savepoint() {
		logs.add(new Savepoint());
		return logs.size();
	}

	int currentSavepointId() {
		return logs.size();
	}

	Savepoint getSavepoint(int savepoint) {
		if (savepoint < 1 || savepoint > logs.size())
			return null;
		return logs.get(savepoint - 1);
	}

	void _rollback(int savepoint) {
		if (savepoint < 1 || savepoint > logs.size())
			throw new XError("zdb: invalid savepoint " + savepoint + "@" + logs.size());
		while (logs.size() >= savepoint)
			logs.remove(logs.size() - 1).rollback();
	}

	private final static ThreadLocal<Transaction> threadlocal = new ThreadLocal<>();
	private final static ThreadLocal<Boolean> isolation = new ThreadLocal<>();
	private final static ReentrantReadWriteLock isolationLock = new ReentrantReadWriteLock(true);

	public static void setIsolationLevel(Isolation level) {
		switch (level) {
		case LEVEL2:
			isolation.remove();
			break;
		case LEVEL3:
			isolation.set(true);
			break;
		}
	}

	public static Isolation getIsolationLevel() {
		return isolation.get() == null ? Isolation.LEVEL2 : Isolation.LEVEL3;
	}

	static Transaction create() {
		Transaction self = threadlocal.get();
		if (self == null)
			threadlocal.set(self = new Transaction());
		return self;
	}

	static void destroy() {
		threadlocal.set(null);
	}

	static Transaction current() {
		return threadlocal.get();
	}

	public static boolean isActive() {
		return current() != null;
	}

	static Savepoint currentSavepoint() {
		Transaction current = current();
		return current.logs.get(current.logs.size() - 1);
	}

	public static int savepoint() {
		return current()._savepoint();
	}

	public static void rollback(int savepoint) {
		current()._rollback(savepoint);
	}

	private void _last_rollback_() {
		try {
			for (int index = logs.size() - 1; index >= 0; --index)
				logs.get(index).rollback();
			logs.clear();
		} catch (Throwable err) {
			Trace.fatal("last rollback ", err);
			Runtime.getRuntime().halt(54321);
		}
	}

	private int _real_commit_(String pname) {
		try {
			int count = 0;
			for (Savepoint sp : logs)
				count += sp.commit();
			logs.clear();
			return count;
		} catch (Throwable t) {
			if (Trace.isWarnEnabled())
				Trace.warn("transaction.commit \"" + pname + "\" throw exception, rollback...", t);
			throw t;
		}
	}

	private void logNotify(ProcedureImpl<?> p) {
		try {
			int maxNestNotify = 255;
			for (int nest = 0; nest < maxNestNotify; ++nest) {
				Map<String, TTable<?, ?>> curLogNotifyTTables = logNotifyTTables;
				logNotifyTTables = new HashMap<>();
				curLogNotifyTTables.values().forEach(t -> t.logNotify());
				if (_real_commit_(p.getProcedureName()) == 0)
					return;
			}
			Trace.fatal("reach maxNestNotify. proc=" + p.getClass().getName());
		} catch (Throwable e) {
			Trace.fatal("logNotify", e);
		}
		_last_rollback_();
		logNotifyTTables.clear();
	}

	private final static AtomicLong totalCount = new AtomicLong();
	private final static AtomicLong totalFalse = new AtomicLong();
	private final static AtomicLong totalException = new AtomicLong();

	public static long getTotalCount() {
		return totalCount.get();
	}

	public static long getTotalFalse() {
		return totalFalse.get();
	}

	public static long getTotalException() {
		return totalException.get();
	}

	void perform(ProcedureImpl<?> p) throws Throwable {
		if (p.getIsolationLevel() == Isolation.LEVEL3) {
			isolationLock.writeLock().lock();
		} else {
			isolationLock.readLock().lock();
		}
		try {
			TransactionMonitor.increment_runned(p.getProcedureName());
			totalCount.incrementAndGet();
			Lock flushLock = Zdb.tables().flushReadLock();
			flushLock.lockInterruptibly();
			try {
				if (p.call()) {
					if (_real_commit_(p.getProcedureName()) > 0)
						logNotify(p);
					last_commit_actions.forEach(Runnable::run);
				} else {
					TransactionMonitor.increment_false(p.getProcedureName());
					totalFalse.incrementAndGet();
					_last_rollback_();
				}
			} catch (Throwable e) {
				_last_rollback_();
				throw e;
			} finally {
				if (duration != null)
					duration.commit();
				last_commit_actions.clear();
				finish();
				flushLock.unlock();
			}
		} catch (Throwable e) {
			p.setException(e);
			p.setSuccess(false);
			TransactionMonitor.increment_exception(p.getProcedureName());
			totalException.incrementAndGet();
			Trace.log(Zdb.pmeta().getTrace(), "Transaction Perform Exception", e);
			throw e;
		} finally {
			if (p.getIsolationLevel() == Isolation.LEVEL3) {
				isolationLock.writeLock().unlock();
			} else {
				isolationLock.readLock().unlock();
			}
		}
	}

	public static void addSavepointTask(Runnable commitTask, Runnable rollbackTask) {
		currentSavepoint().add(new Log() {
			@Override
			public void commit() {
				if (commitTask != null)
					commitTask.run();
			}

			@Override
			public void rollback() {
				if (rollbackTask != null)
					rollbackTask.run();
			}
		});
	}

	/**
	 * LockContext manager.
	 * <p>
	 * The LockContext is created when the Transaction.getLockContext() is
	 * called firstly, and destroyed when the transaction is finished.Call the
	 * method lock(), submit and clear the lock histories added previously.
	 * <p>
	 * In *Add method, when the parameter of Object type is detected as the
	 * Collection object or Array, the parameter is extracted recursively and
	 * classify into TTable object and other object as key, since table and key
	 * could neither be Collection object nor Array.
	 * 
	 */
	public final class LockContext {
		private final Collection<LockeyHolder> lockCollection = new ArrayList<>();

		private LockContext() {
		}

		private void classify(Object obj, Collection<Object> keys, Collection<TTable<?, ?>> ttables) {
			if (obj.getClass().isArray()) {
				int len = Array.getLength(obj);
				for (int i = 0; i < len; i++)
					classify(Array.get(obj, i), keys, ttables);
			} else if (obj instanceof Collection<?>)
				for (Object o : (Collection<?>) obj)
					classify(o, keys, ttables);
			else if (obj instanceof TTable<?, ?>)
				ttables.add((TTable<?, ?>) obj);
			else
				keys.add(obj);
		}

		private LockContext add(LockeyHolderType type, Object key, TTable<?, ?>... ttables) {
			if (key instanceof Collection<?>) {
				Collection<Object> keys = new ArrayList<>();
				Collection<TTable<?, ?>> ttables2 = new ArrayList<>();
				classify(key, keys, ttables2);
				Stream.concat(Arrays.stream(ttables), ttables2.stream()).map(t -> t.getLockId()).distinct()
						.forEach(lockId -> {
							for (Object k : keys)
								lockCollection.add(new LockeyHolder(Lockeys.getLockey(lockId, k), type));
						});
			} else
				Arrays.stream(ttables).map(t -> t.getLockId()).distinct()
						.forEach(lockId -> lockCollection.add(new LockeyHolder(Lockeys.getLockey(lockId, key), type)));
			return this;
		}

		private LockContext add(LockeyHolderType type, Object... mix) {
			Collection<Object> keys = new ArrayList<>();
			Collection<TTable<?, ?>> ttables = new ArrayList<>();
			for (Object obj : mix)
				classify(obj, keys, ttables);
			ttables.stream().map(t -> t.getLockId()).distinct().forEach(lockId -> {
				for (Object key : keys)
					lockCollection.add(new LockeyHolder(Lockeys.getLockey(lockId, key), type));
			});
			return this;
		}

		public LockContext rAdd(Object key, TTable<?, ?>... ttables) {
			return add(LockeyHolderType.READ, key, ttables);
		}

		public LockContext rAdd(Object... mix) {
			return add(LockeyHolderType.READ, mix);
		}

		public LockContext wAdd(Object key, TTable<?, ?>... ttables) {
			return add(LockeyHolderType.WRITE, key, ttables);
		}

		public LockContext wAdd(Object... mix) {
			return add(LockeyHolderType.WRITE, mix);
		}

		public void lock() {
			lockCollection.stream().sorted().forEach(holder -> {
				if (holder.type == LockeyHolderType.WRITE)
					wAddLockey(holder.lockey);
				else
					rAddLockey(holder.lockey);
			});
			lockCollection.clear();
		}
	}

	private LockContext lockContext;

	private LockContext _getLockContext() {
		if (lockContext == null)
			lockContext = new LockContext();
		return lockContext;
	}

	public static LockContext getLockContext() {
		Transaction current = current();
		if (current == null)
			throw new IllegalStateException("createLockContext out of Transaction.");
		return current._getLockContext();
	}
}
