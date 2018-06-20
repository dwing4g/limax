package limax.util.monitor;

public interface MonitorSet<Key extends GroupKeys> {

	void increment(String name, Key key);

	void increment(String name, Key key, long delta);

	void set(String name, Key key, long value);

	long get(String name, Key key);

	static <Key extends GroupKeys> MonitorSet<Key> create(Class<?> cls, boolean supportTransaction,
			String... counternames) {
		return MonitorImpl.createSet(cls, supportTransaction, counternames);
	}
}