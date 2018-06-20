package limax.zdb;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import limax.zdb.TRecord.State;

final class LogRecord<K, V> {
	private final static Map<Class<?>, Listenable> cache = new ConcurrentHashMap<>();
	private final TTable<K, V> table;
	private final Map<K, LogR<K, V>> changed = new HashMap<>();
	private final Listenable seed;
	private Boolean hasListener;

	LogRecord(TTable<K, V> table) {
		Object o = table.newValue();
		this.table = table;
		this.seed = o == null ? Listenable.defaultListenable
				: cache.computeIfAbsent(o.getClass(), k -> Listenable.create(o));
	}

	private LogR<K, V> getLogR(TRecord<K, V> r) {
		if (hasListener == null) {
			hasListener = table.hasListener();
			Transaction.current().recordLogNotifyTTable(table);
		}
		return hasListener ? changed.computeIfAbsent(r.getKey(), v -> new LogR<K, V>(r, seed.copy())) : null;
	}

	void onChanged(TRecord<K, V> r, boolean cc, State ss) {
		LogR<K, V> lr = getLogR(r);
		if (lr != null && lr.ss == null) {
			lr.cc = cc;
			lr.ss = ss;
		}
	}

	void onChanged(TRecord<K, V> r, LogNotify ln) {
		LogR<K, V> lr = getLogR(r);
		if (lr != null) {
			ln.pop();
			lr.l.setChanged(ln);
		}
	}

	void logNotify(ListenerMap listenerMap) {
		changed.forEach((k, lr) -> lr.l.logNotify(k, lr.r.getValue(), lr.getRecordState(), listenerMap));
		changed.clear();
		hasListener = null;
	}

	private static class LogR<K, V> {
		private final TRecord<K, V> r;
		private final Listenable l;
		private boolean cc;
		private State ss;

		LogR(TRecord<K, V> r, Listenable l) {
			this.r = r;
			this.l = l;
		}

		private RecordState getRecordState() {
			if (null == ss)
				return RecordState.CHANGED;
			if (cc && State.ADD == ss && State.REMOVE == r.state())
				return RecordState.NONE;
			if (!cc && State.ADD == ss && State.REMOVE == r.state())
				return RecordState.REMOVED;
			if (State.ADD == ss && State.ADD == r.state())
				return RecordState.ADDED;
			if (State.INDB_GET == ss && State.INDB_REMOVE == r.state())
				return RecordState.REMOVED;
			if (State.INDB_GET == ss && State.INDB_ADD == r.state())
				return RecordState.ADDED;
			if (State.INDB_REMOVE == ss && State.INDB_ADD == r.state())
				return RecordState.ADDED;
			if (cc && State.INDB_REMOVE == ss && State.INDB_REMOVE == r.state())
				return RecordState.REMOVED;
			if (!cc && State.INDB_REMOVE == ss && State.INDB_REMOVE == r.state())
				return RecordState.NONE;
			if (cc && State.REMOVE == ss && State.REMOVE == r.state())
				return RecordState.NONE;
			if (cc && State.REMOVE == ss && State.ADD == r.state())
				return RecordState.ADDED;
			if (!cc && State.INDB_ADD == ss && State.INDB_ADD == r.state())
				return RecordState.ADDED;
			if (!cc && State.INDB_ADD == ss && State.INDB_REMOVE == r.state())
				return RecordState.REMOVED;
			throw new IllegalStateException(
					"LogRecord Error! isCreateCache = " + cc + ", SavedState = " + ss + ", State = " + r.state());
		}
	}
}
