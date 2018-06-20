package limax.util.monitor;

public interface Group {
	void increment(String name);

	void increment(String name, long delta);

	void set(String name, long value);

	long get(String name);

	static Group create(Class<?> cls, boolean supportTransaction, String... counternames) {
		return MonitorImpl.createGroup(cls, supportTransaction, counternames);
	}
}