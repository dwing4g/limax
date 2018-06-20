package limax.util.monitor;

public interface MapKeySet<MapKey, RawKey extends GroupKeys> extends MonitorSet<RawKey> {

	void mapKey(MapKey k, RawKey v);

	void increment(String name, MapKey key);

	void increment(String name, MapKey key, long delta);

	void set(String name, MapKey key, long value);

	static <MapKey, RawKey extends GroupKeys> MapKeySet<MapKey, RawKey> create(Class<?> cls, boolean supportTransaction,
			String... counternames) {
		return MonitorImpl.createMapKeySet(cls, supportTransaction, counternames);
	}
}