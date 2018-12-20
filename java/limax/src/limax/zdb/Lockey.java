package limax.zdb;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import limax.util.LockEnvironment;

final class Lockey implements Comparable<Lockey> {
	private final int index;
	private final Object key;
	private final int hashcode;
	private volatile LockEnvironment.DetectableLock rlock;
	private volatile LockEnvironment.DetectableLock wlock;

	Lockey(int id, Object key) {
		this.index = id;
		this.key = key;
		this.hashcode = id ^ (id << 16) ^ key.hashCode();
	}

	Lockey alloc() {
		ReentrantReadWriteLock rwlock = new ReentrantReadWriteLock();
		rlock = Zdb.lockEnvironment().create(rwlock.readLock(), rwlock);
		wlock = Zdb.lockEnvironment().create(rwlock.writeLock(), rwlock);
		return this;
	}

	Object getKey() {
		return key;
	}

	void rUnlock() {
		rlock.unlock();
	}

	void wUnlock() {
		wlock.unlock();
	}

	boolean rTryLock() {
		return rlock.tryLock();
	}

	boolean wTryLock() {
		return wlock.tryLock();
	}

	void rLock() {
		try {
			rlock.lock();
		} catch (InterruptedException e) {
			throw new XDeadlock();
		}
	}

	void wLock() {
		try {
			wlock.lock();
		} catch (InterruptedException e) {
			throw new XDeadlock();
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public int compareTo(Lockey o) {
		int x = index - o.index;
		return x != 0 ? x : ((Comparable<Object>) key).compareTo(o.key);
	}

	@Override
	public int hashCode() {
		return hashcode;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj instanceof Lockey) {
			Lockey o = (Lockey) obj;
			return this.index == o.index && key.equals(o.key);
		}
		return false;
	}

	@Override
	public String toString() {
		return index + "." + key;
	}
}
