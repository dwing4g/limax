package limax.zdb;

import limax.codec.MarshalException;
import limax.codec.OctetsStream;
import limax.util.Trace;

final class TRecord<K, V> extends XBean {
	private final TTable<K, V> table;
	private final Lockey lockey;
	private V value;
	private State state;

	private volatile long lastAccessTime = System.nanoTime();

	void access() {
		lastAccessTime = System.nanoTime();
	}

	long getLastAccessTime() {
		return lastAccessTime;
	}

	static enum State {
		INDB_GET, INDB_ADD, INDB_REMOVE, ADD, REMOVE
	}

	@Override
	public String toString() {
		return table.getName() + "," + lockey + "," + state;
	}

	@SuppressWarnings("unchecked")
	K getKey() {
		return (K) lockey.getKey();
	}

	private OctetsStream marshalKey() {
		return table.marshalKey(getKey());
	}

	private OctetsStream marshalValue() {
		return table.marshalValue(value);
	}

	State state() {
		return state;
	}

	@Override
	void notify(LogNotify notify) {
		table.onRecordChanged(this, notify);
	}

	Lockey getLockey() {
		return lockey;
	}

	private static final String RECORD_VARNAME = "value";

	LogKey getLogKey() {
		return new LogKey(this, RECORD_VARNAME);
	}

	TRecord(TTable<K, V> table, V value, Lockey lockey, State state) {
		super(null, RECORD_VARNAME);
		this.table = table;
		if (null != value)
			Logs.link(value, this, RECORD_VARNAME, State.INDB_GET != state);
		this.value = value;
		this.lockey = lockey;
		this.state = state;
	}

	private void _remove() {
		Logs.link(value, null, null);
		Transaction.currentSavepoint().addIfAbsent(getLogKey(), new LogAddRemove());
		value = null;
	}

	boolean remove() {
		switch (state) {
		case INDB_GET:
			_remove();
			state = State.INDB_REMOVE;
			return true;
		case INDB_ADD:
			_remove();
			state = State.INDB_REMOVE;
			return true;
		case ADD:
			_remove();
			state = State.REMOVE;
			return true;
		default:
			return false;
		}
	}

	private void _add(V value) {
		Logs.link(value, this, RECORD_VARNAME);
		Transaction.currentSavepoint().addIfAbsent(getLogKey(), new LogAddRemove());
		this.value = value;
	}

	boolean add(V value) {
		switch (state) {
		case INDB_REMOVE:
			_add(value);
			state = State.INDB_ADD;
			return true;
		case REMOVE:
			_add(value);
			state = State.ADD;
			return true;
		default:
			return false;
		}
	}

	V getValue() {
		return value;
	}

	private class LogAddRemove implements Log {
		private final V saved_value;
		private final State saved_state;

		LogAddRemove() {
			saved_value = value;
			saved_state = state;
		}

		@Override
		public void commit() {
			table.onRecordChanged(TRecord.this, false, saved_state);
		}

		@Override
		public void rollback() {
			value = saved_value;
			state = saved_state;
		}

		@Override
		public String toString() {
			return "state=" + saved_state + " value=" + saved_value;
		}
	}

	private OctetsStream snapshotKey = null;
	private OctetsStream snapshotValue = null;
	private State snapshotState = null;

	boolean tryMarshalN(Runnable action) {
		if (!lockey.rTryLock())
			return false;
		try {
			marshal0();
			action.run();
		} finally {
			lockey.rUnlock();
		}
		return true;
	}

	void marshal0() {
		switch (state) {
		case ADD:
		case INDB_GET:
		case INDB_ADD:
			snapshotKey = marshalKey();
			snapshotValue = marshalValue();
			break;
		case INDB_REMOVE:
			snapshotKey = marshalKey();
		case REMOVE:
		}
	}

	void snapshot() {
		switch (snapshotState = state) {
		case ADD:
		case INDB_ADD:
			state = State.INDB_GET;
			break;
		case REMOVE:
		case INDB_REMOVE:
			table.getCache().remove(getKey());
		case INDB_GET:
		}
	}

	boolean flush(TStorage<K, V> storage) {
		switch (snapshotState) {
		case INDB_ADD:
		case INDB_GET:
			storage.flushKeySize += snapshotKey.size();
			storage.flushValueSize += snapshotValue.size();
			storage.getEngine().replace(snapshotKey, snapshotValue);
			return true;
		case ADD:
			storage.flushKeySize += snapshotKey.size();
			storage.flushValueSize += snapshotValue.size();
			if (!storage.getEngine().insert(snapshotKey, snapshotValue))
				throw new XError("insert fail");
			return true;
		case INDB_REMOVE:
			storage.flushKeySize += snapshotKey.size();
			storage.getEngine().remove(snapshotKey);
			return true;
		case REMOVE:
		}
		return false;
	}

	void clear() {
		snapshotKey = null;
		snapshotValue = null;
		snapshotState = null;
	}

	boolean exist() {
		if (Trace.isDebugEnabled())
			Trace.debug("TRecord.exist " + this);
		switch (snapshotState) {
		case INDB_GET:
		case INDB_ADD:
		case ADD:
			return true;
		case INDB_REMOVE:
		case REMOVE:
		}
		return false;
	}

	OctetsStream find() {
		if (Trace.isDebugEnabled())
			Trace.debug("TRecord.find " + this);
		switch (snapshotState) {
		case INDB_GET:
		case INDB_ADD:
		case ADD:
			return snapshotValue;
		case INDB_REMOVE:
		case REMOVE:
		}
		return null;
	}

	@Override
	public OctetsStream marshal(OctetsStream arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public OctetsStream unmarshal(OctetsStream arg0) throws MarshalException {
		throw new UnsupportedOperationException();
	}

}
