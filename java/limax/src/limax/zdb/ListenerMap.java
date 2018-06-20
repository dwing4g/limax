package limax.zdb;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import limax.util.Trace;

final class ListenerMap {
	private final Map<String, Set<Listener>> listenerMap = new HashMap<>();
	private final Lock sync = new ReentrantLock();
	private volatile Map<String, Set<Listener>> listenerMapCopy = new HashMap<>();

	private void setListenerMapCopy() {
		this.listenerMapCopy = listenerMap.entrySet().stream()
				.collect(Collectors.toMap(e -> e.getKey(), e -> new HashSet<>(e.getValue())));
	}

	Runnable add(String name, Listener l) {
		sync.lock();
		try {
			listenerMap.computeIfAbsent(name, v -> new HashSet<>()).add(l);
			setListenerMapCopy();
			return () -> {
				sync.lock();
				try {
					listenerMap.computeIfPresent(name, (k, v) -> {
						if (v.remove(l))
							setListenerMapCopy();
						return v.isEmpty() ? null : v;
					});
				} finally {
					sync.unlock();
				}
			};
		} finally {
			sync.unlock();
		}
	}

	boolean hasListener() {
		Map<String, Set<Listener>> localCopy = this.listenerMapCopy;
		return !localCopy.isEmpty();
	}

	boolean hasListener(String fullVarName) {
		Map<String, Set<Listener>> localCopy = this.listenerMapCopy;
		return null != localCopy.get(fullVarName);
	}

	void notifyChanged(String fullVarName, Object key, Object value) {
		notify(ChangeKind.CHANGED_ALL, fullVarName, key, value, null);
	}

	void notifyRemoved(String fullVarName, Object key, Object value) {
		notify(ChangeKind.REMOVED, fullVarName, key, value, null);
	}

	void notifyChanged(String fullVarName, Object key, Object value, Note note) {
		notify(ChangeKind.CHANGED_NOTE, fullVarName, key, value, note);
	}

	private enum ChangeKind {
		CHANGED_ALL, CHANGED_NOTE, REMOVED
	}

	private void notify(ChangeKind kind, String fullVarName, Object key, Object value, Note note) {
		Map<String, Set<Listener>> localCopy = this.listenerMapCopy;
		Set<Listener> listeners = localCopy.get(fullVarName);
		if (null == listeners)
			return;
		for (Listener l : listeners) {
			Transaction trans = Transaction.current();
			int spBefore = trans.currentSavepointId();
			int spBeforeAccess = spBefore > 0 ? trans.getSavepoint(spBefore).getAccess() : 0;
			try {
				switch (kind) {
				case CHANGED_ALL:
					l.onChanged(key, value);
					break;
				case CHANGED_NOTE:
					l.onChanged(key, value, fullVarName, note);
					break;
				case REMOVED:
					l.onRemoved(key, value);
					break;
				}
			} catch (Throwable e) {
				if (Trace.isErrorEnabled())
					Trace.error("doChanged key=" + key + " name=" + fullVarName, e);
				int spAfter = trans.currentSavepointId();
				if (0 == spBefore) {
					if (spAfter > 0)
						trans._rollback(spBefore + 1);
				} else {
					if (spAfter < spBefore)
						throw new IllegalStateException("spAfter < spBefore");
					if (trans.getSavepoint(spBefore).isAccessSince(spBeforeAccess))
						trans._rollback(spBefore);
					else if (spAfter > spBefore)
						trans._rollback(spBefore + 1);
				}
			}
		}
	}
}
