package limax.util;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

public final class MapBitSet<K> {
	private final Map<K, BitSet> map = new HashMap<K, BitSet>();

	public void clear(K key, int index) {
		BitSet bs = map.get(key);
		if (bs != null) {
			bs.clear(index);
			if (bs.isEmpty())
				map.remove(key);
		}
	}

	public void set(K key, int index) {
		BitSet bs = map.get(key);
		if (bs == null)
			map.put(key, bs = new BitSet());
		bs.set(index);
	}

	public boolean get(K key, int index) {
		BitSet bs = map.get(key);
		return bs == null ? false : bs.get(index);
	}

	public void remove(K key) {
		map.remove(key);
	}
}
