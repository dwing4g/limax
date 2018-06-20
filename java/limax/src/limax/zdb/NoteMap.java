package limax.zdb;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class NoteMap<K, V> implements Note {
	private final Set<K> added = new HashSet<>();
	private final Map<K, V> removed = new HashMap<>();
	private final Map<K, V> replaced = new HashMap<>();
	private List<V> changed;
	private Map<K, V> objref;
	private Map<K, V> changedmap;

	@SuppressWarnings("unchecked")
	final void setChanged(List<V> changed, Object objref) {
		this.changed = changed;
		this.objref = (Map<K, V>) objref;
	}

	final void merge(Note note) {
		@SuppressWarnings("unchecked")
		NoteMap<K, V> another = (NoteMap<K, V>) note;
		another.added.forEach(k -> logPut(k, null, null));
		another.removed.forEach((k, v) -> logRemove(k, v));
		another.replaced.forEach((k, v) -> logPut(k, v, v));
	}

	final Set<K> getAdded() {
		return added;
	}

	final Map<K, V> getReplaced() {
		return replaced;
	}

	public final Map<K, V> getRemoved() {
		return removed;
	}

	public final Map<K, V> getChanged() {
		if (changedmap != null)
			return changedmap;
		changedmap = replaced.keySet().stream().collect(Collectors.toMap(Function.identity(), key -> objref.get(key)));
		if (changed == null && added.isEmpty())
			return changedmap;
		added.forEach(key -> changedmap.put(key, objref.get(key)));
		if (changed != null) {
			Set<V> set = Collections.newSetFromMap(new IdentityHashMap<>());
			set.addAll(changed);
			objref.entrySet().stream().filter(e -> set.contains(e.getValue()))
					.forEach(e -> changedmap.put(e.getKey(), e.getValue()));
		}
		return changedmap;
	}

	final boolean isMapChanged() {
		return !added.isEmpty() || !removed.isEmpty() || !replaced.isEmpty();
	}

	final void clear() {
		added.clear();
		removed.clear();
		replaced.clear();
		objref = null;
		changed = null;
		changedmap = null;
	}

	final void logRemove(K key, V value) {
		if (added.remove(key))
			return;
		V v = replaced.remove(key);
		removed.put(key, v == null ? value : v);
	}

	final void logPut(K key, V origin, V value) {
		if (added.contains(key))
			return;
		V v = removed.remove(key);
		if (null != v) {
			replaced.put(key, v);
			return;
		}
		if (replaced.containsKey(key))
			return;
		if (null == origin)
			added.add(key);
		else
			replaced.put(key, origin);
	}

	@Override
	public String toString() {
		return "added=" + added + " replaced=" + replaced + " removed=" + removed + " changed=" + changed;
	}
}
