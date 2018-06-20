package limax.zdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Savepoint {
	private final Map<Object, Log> logs = new HashMap<>();
	private final List<Log> addOrder = new ArrayList<Log>();
	private int access = 0;

	Savepoint() {
	}

	int commit() {
		for (Log log : addOrder)
			log.commit();
		return addOrder.size();
	}

	int rollback() {
		for (int i = addOrder.size() - 1; i >= 0; --i)
			addOrder.get(i).rollback();
		return addOrder.size();
	}

	boolean isAccessSince(int a) {
		return a != access;
	}

	int getAccess() {
		return access;
	}

	Log get(LogKey key) {
		++access;
		return logs.get(key);
	}

	void add(Log log) {
		addOrder.add(log);
	}

	void add(LogKey key, Log log) {
		++access;
		Log old = logs.put(key, log);
		if (null != old) {
			logs.put(key, old);
			throw new XError("impossible limax.zdb.Savepoint.add duplicate log");
		}
		addOrder.add(log);
	}

	boolean addIfAbsent(Object key, Log log) {
		++access;
		if (logs.containsKey(key))
			return false;
		logs.put(key, log);
		addOrder.add(log);
		return true;
	}
}
