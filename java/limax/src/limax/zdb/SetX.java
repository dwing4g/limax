package limax.zdb;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

public final class SetX<E> extends AbstractSet<E> implements Set<E> {
	private transient java.util.HashMap<E, E> map;

	public SetX() {
		map = new java.util.HashMap<E, E>();
	}

	public SetX(int initialCapacity) {
		map = new java.util.HashMap<E, E>(initialCapacity);
	}

	public SetX(int initialCapacity, float loadFactor) {
		map = new java.util.HashMap<E, E>(initialCapacity, loadFactor);
	}

	@Override
	public Iterator<E> iterator() {
		return map.keySet().iterator();
	}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return map.containsKey(o);
	}

	@Override
	public boolean add(E e) {
		if (null == e)
			throw new NullPointerException();
		if (map.containsKey(e))
			return false;
		map.put(e, e);
		return true;
	}

	public E removex(Object o) {
		return map.remove(o);
	}

	@Override
	public boolean remove(Object o) {
		return null != removex(o);
	}

	@Override
	public void clear() {
		map.clear();
	}

}
