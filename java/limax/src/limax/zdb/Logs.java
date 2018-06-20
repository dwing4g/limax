package limax.zdb;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class Logs {
	private Logs() {
	}

	public static void logObject(XBean xbean, String varname) {
		LogKey key = new LogKey(xbean, varname);
		Savepoint sp = Transaction.currentSavepoint();
		if (sp.get(key) == null)
			sp.add(key, new LogObject(key));
	}

	@SuppressWarnings("unchecked")
	public static <E> List<E> logList(XBean xbean, String varname, Runnable verify) {
		LogKey key = new LogKey(xbean, varname);
		Map<LogKey, Object> wrappers = Transaction.current().wrappers;
		LogList<E> log = (LogList<E>) wrappers.get(key);
		if (log == null)
			wrappers.put(key, log = new LogList<E>(key, (List<E>) key.getValue()));
		return log.setVerify(verify);
	}

	@SuppressWarnings("unchecked")
	public static <E> Set<E> logSet(XBean xbean, String varname, Runnable verify) {
		LogKey key = new LogKey(xbean, varname);
		Map<LogKey, Object> wmap = Transaction.current().wrappers;
		LogSet<E> log = (LogSet<E>) wmap.get(key);
		if (log == null)
			wmap.put(key, log = new LogSet<E>(key, (SetX<E>) key.getValue()));
		return log.setVerify(verify);
	}

	@SuppressWarnings("unchecked")
	public static <K, V> Map<K, V> logMap(XBean xbean, String varname, Runnable verify) {
		LogKey key = new LogKey(xbean, varname);
		Map<LogKey, Object> wmap = Transaction.current().wrappers;
		LogMap<K, V, Map<K, V>> log = (LogMap<K, V, Map<K, V>>) wmap.get(key);
		if (log == null)
			wmap.put(key, log = new LogMap<K, V, Map<K, V>>(key, (Map<K, V>) key.getValue()));
		return log.setVerify(verify);
	}

	static void link(Object bean, XBean parent, String varname, boolean log) {
		Objects.requireNonNull(bean);
		if (bean instanceof XBean)
			((XBean) bean).link(parent, varname, log);
	}

	static void link(Object bean, XBean parent, String varname) {
		link(bean, parent, varname, true);
	}
}

class LogObject implements Note, Log {
	private final LogKey logkey;
	private final Object origin;

	public LogObject(LogKey logkey) {
		this.logkey = logkey;
		this.origin = logkey.getValue();
	}

	@Override
	public void commit() {
		LogNotify.notify(logkey, this);
	}

	@Override
	public void rollback() {
		logkey.setValue(origin);
	}

	@Override
	public String toString() {
		return String.valueOf(origin);
	}
}

class WrapList<E> implements List<E> {
	private LogList<E> root;
	private List<E> wrapped;

	public WrapList(LogList<E> root, List<E> wrapped) {
		this.root = root;
		this.wrapped = wrapped;
	}

	protected void beforeChange() {
		root.beforeChange();
	}

	protected void afterAdd(E add) {
		root.afterAdd(add);
	}

	protected void beforeRemove(E remove) {
		root.beforeRemove(remove);
	}

	protected List<E> getWrapped() {
		return wrapped;
	}

	@Override
	public boolean add(E e) {
		beforeChange();
		if (wrapped.add(e)) {
			afterAdd(e);
			return true;
		}
		return false;
	}

	@Override
	public void add(int index, E element) {
		beforeChange();
		wrapped.add(index, element);
		afterAdd(element);
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		beforeChange();
		if (wrapped.addAll(c)) {
			for (E e : c)
				afterAdd(e);
			return true;
		}
		return false;
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		beforeChange();
		if (wrapped.addAll(index, c)) {
			for (E e : c)
				afterAdd(e);
			return true;
		}
		return false;
	}

	@Override
	public void clear() {
		beforeChange();
		for (E e : wrapped)
			beforeRemove(e);
		wrapped.clear();
	}

	@Override
	public boolean contains(Object o) {
		return wrapped.contains(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return wrapped.containsAll(c);
	}

	@Override
	public boolean equals(Object obj) {
		return wrapped.equals(obj);
	}

	@Override
	public E get(int index) {
		return wrapped.get(index);
	}

	@Override
	public int hashCode() {
		return wrapped.hashCode();
	}

	@Override
	public int indexOf(Object o) {
		return wrapped.indexOf(o);
	}

	@Override
	public boolean isEmpty() {
		return wrapped.isEmpty();
	}

	private class WrapIt implements Iterator<E> {
		private Iterator<E> it = wrapped.iterator();
		private E current;

		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public E next() {
			return current = it.next();
		}

		@Override
		public void remove() {
			beforeChange();
			beforeRemove(current);
			it.remove();
		}
	}

	@Override
	public Iterator<E> iterator() {
		return new WrapIt();
	}

	@Override
	public int lastIndexOf(Object o) {
		return wrapped.lastIndexOf(o);
	}

	private class WrapListIt implements ListIterator<E> {
		private ListIterator<E> it;
		private E current;

		WrapListIt() {
			it = wrapped.listIterator();
		}

		WrapListIt(int index) {
			it = wrapped.listIterator(index);
		}

		@Override
		public void add(E e) {
			beforeChange();
			it.add(e);
			afterAdd(e);
		}

		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public boolean hasPrevious() {
			return it.hasPrevious();
		}

		@Override
		public E next() {
			return current = it.next();
		}

		@Override
		public int nextIndex() {
			return it.nextIndex();
		}

		@Override
		public E previous() {
			return current = it.previous();
		}

		@Override
		public int previousIndex() {
			return it.previousIndex();
		}

		@Override
		public void remove() {
			beforeChange();
			beforeRemove(current);
			it.remove();
		}

		@Override
		public void set(E e) {
			beforeChange();
			beforeRemove(current);
			it.set(e);
			afterAdd(e);
		}
	}

	@Override
	public ListIterator<E> listIterator() {
		return new WrapListIt();
	}

	@Override
	public ListIterator<E> listIterator(int index) {
		return new WrapListIt(index);
	}

	@Override
	public E remove(int index) {
		beforeChange();
		E removed = wrapped.remove(index);
		beforeRemove(removed);
		return removed;
	}

	@Override
	public boolean remove(Object o) {
		int index = this.indexOf(o);
		if (index < 0)
			return false;
		this.remove(index);
		return true;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		boolean modified = false;
		Iterator<?> e = iterator();
		while (e.hasNext()) {
			if (c.contains(e.next())) {
				e.remove();
				modified = true;
			}
		}
		return modified;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		boolean modified = false;
		Iterator<E> e = iterator();
		while (e.hasNext()) {
			if (!c.contains(e.next())) {
				e.remove();
				modified = true;
			}
		}
		return modified;
	}

	@Override
	public E set(int index, E element) {
		beforeChange();
		E removed = wrapped.set(index, element);
		beforeRemove(removed);
		afterAdd(element);
		return removed;
	}

	@Override
	public int size() {
		return wrapped.size();
	}

	@Override
	public List<E> subList(int fromIndex, int toIndex) {
		return new WrapList<E>(root, wrapped.subList(fromIndex, toIndex));
	}

	@Override
	public Object[] toArray() {
		return wrapped.toArray();
	}

	@Override
	public <X> X[] toArray(X[] a) {
		return wrapped.toArray(a);
	}

	@Override
	public String toString() {
		return wrapped.toString();
	}
}

class LogList<E> extends WrapList<E> {
	private final LogKey logkey;
	private Runnable verify;

	private final class MyLog implements Note, Log {
		private Object[] savedonwrite;

		@Override
		public void commit() {
			if (savedonwrite != null)
				LogNotify.notify(logkey, this);
		}

		@Override
		public void rollback() {
			getWrapped().clear();
			for (int i = 0; i < savedonwrite.length; ++i) {
				@SuppressWarnings("unchecked")
				E e = (E) savedonwrite[i];
				getWrapped().add(e);
			}
		}

		void beforeChange() {
			if (savedonwrite == null)
				savedonwrite = getWrapped().toArray();
		}
	}

	@SuppressWarnings("unchecked")
	private final MyLog myLog() {
		Savepoint sp = Transaction.currentSavepoint();
		Log log = sp.get(logkey);
		if (null == log)
			sp.add(logkey, log = new MyLog());
		return (MyLog) log;
	}

	@Override
	protected final void beforeChange() {
		verify.run();
		myLog().beforeChange();
	}

	@Override
	protected void afterAdd(E add) {
		Logs.link(add, logkey.getXBean(), logkey.getVarname());
	}

	@Override
	protected void beforeRemove(E remove) {
		Logs.link(remove, null, null);
	}

	@Override
	public List<E> subList(int fromIndex, int toIndex) {
		return new WrapList<E>(this, getWrapped().subList(fromIndex, toIndex));
	}

	public LogList(LogKey logkey, List<E> wrapped) {
		super(null, wrapped);
		this.logkey = logkey;
	}

	LogList<E> setVerify(Runnable verify) {
		this.verify = verify;
		return this;
	}
}

class LogSet<E> extends AbstractSet<E> {

	private final LogKey logkey;
	private final SetX<E> wrapped;
	private Runnable verify;

	private final class MyLog extends NoteSet<E> implements Log {
		@Override
		public void commit() {
			if (isSetChanged())
				LogNotify.notify(logkey, this);
		}

		@Override
		public void rollback() {
			wrapped.removeAll(getAdded());
			wrapped.addAll(getRemoved());
			wrapped.removeAll(getEldest());
			wrapped.addAll(getEldest());
			clear();
		}

		private void beforeRemove(E e) {
			Logs.link(e, null, null);
			logRemove(e);
		}

		private void afterRemove(E e) {
			logRemove(e);
			Logs.link(e, null, null);
		}

		private void afterAdd(E e) {
			logAdd(e);
		}
	}

	@SuppressWarnings("unchecked")
	private final MyLog myLog() {
		Savepoint sp = Transaction.currentSavepoint();
		Log log = sp.get(logkey);
		if (null == log)
			sp.add(logkey, log = new MyLog());
		return (MyLog) log;
	}

	public LogSet(LogKey logkey, SetX<E> wrapped) {
		this.logkey = logkey;
		this.wrapped = wrapped;
	}

	LogSet<E> setVerify(Runnable verify) {
		this.verify = verify;
		return this;
	}

	@Override
	public boolean add(E e) {
		verify.run();
		if (wrapped.add(e)) {
			myLog().afterAdd(e);
			Logs.link(e, logkey.getXBean(), logkey.getVarname());
			return true;
		}
		return false;
	}

	@Override
	public void clear() {
		verify.run();
		for (Iterator<E> it = iterator(); it.hasNext();)
			myLog().beforeRemove(it.next());
		wrapped.clear();
	}

	@Override
	public boolean contains(Object o) {
		return wrapped.contains(o);
	}

	private class WrapIt implements Iterator<E> {
		private final Iterator<E> it = wrapped.iterator();
		E current;

		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public E next() {
			return current = it.next();
		}

		@Override
		public void remove() {
			verify.run();
			myLog().beforeRemove(current);
			it.remove();
		}
	}

	@Override
	public Iterator<E> iterator() {
		return new WrapIt();
	}

	@Override
	public boolean remove(Object o) {
		verify.run();
		E e = wrapped.removex(o);
		if (e != null) {
			myLog().afterRemove(e);
			return true;
		}
		return false;
	}

	@Override
	public int size() {
		return wrapped.size();
	}

	@Override
	public int hashCode() {
		return wrapped.hashCode();
	}
}

class LogMap<K, V, W extends Map<K, V>> implements Map<K, V> {

	final LogKey logkey;
	final W wrapped;
	private Runnable verify;

	public LogMap(LogKey logkey, W wrapped) {
		this.logkey = logkey;
		this.wrapped = wrapped;
	}

	LogMap<K, V, W> setVerify(Runnable verify) {
		this.verify = verify;
		return this;
	}

	final class MyLog extends NoteMap<K, V> implements Log {
		@Override
		public void commit() {
			if (isMapChanged())
				LogNotify.notify(logkey, this);
		}

		@Override
		public void rollback() {
			wrapped.keySet().removeAll(getAdded());
			wrapped.putAll(getRemoved());
			wrapped.putAll(getReplaced());
			clear();
		}

		private void beforeRemove(K key, V value) {
			Logs.link(value, null, null);
			logRemove(key, value);
		}

		void afterRemove(K key, V value) {
			logRemove(key, value);
			Logs.link(value, null, null);
		}

		private void afterPut(K key, V origin, V value) {
			logPut(key, origin, value);
			if (null != origin)
				Logs.link(origin, null, null);
		}
	}

	@SuppressWarnings("unchecked")
	final MyLog myLog() {
		Savepoint sp = Transaction.currentSavepoint();
		Log log = sp.get(logkey);
		if (null == log)
			sp.add(logkey, log = new MyLog());
		return (MyLog) log;
	}

	@Override
	public void clear() {
		verify.run();
		wrapped.forEach((key, value) -> myLog().beforeRemove(key, value));
		wrapped.clear();
	}

	@Override
	public boolean containsKey(Object key) {
		return wrapped.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return wrapped.containsValue(value);
	}

	abstract class WrapEntryIt<E> implements Iterator<E> {
		private final Iterator<Entry<K, V>> it;
		private WrapEntry current;

		WrapEntryIt() {
			this.it = wrapped.entrySet().iterator();
		}

		WrapEntryIt(Iterator<Entry<K, V>> it) {
			this.it = it;
		}

		@Override
		public void remove() {
			verify.run();
			myLog().beforeRemove(current.getKey(), current.getValue());
			it.remove();
		}

		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		Entry<K, V> nextEntry() {
			return current = new WrapEntry(it.next());
		}
	}

	private Iterator<Entry<K, V>> newEntryIterator() {
		return new WrapEntryIt<Entry<K, V>>() {
			public Entry<K, V> next() {
				return nextEntry();
			}
		};
	}

	final class WrapEntry implements Map.Entry<K, V> {
		private Map.Entry<K, V> e;

		WrapEntry(Map.Entry<K, V> e) {
			this.e = e;
		}

		@Override
		public boolean equals(Object obj) {
			return e.equals(obj);
		}

		@Override
		public K getKey() {
			return e.getKey();
		}

		@Override
		public V getValue() {
			return e.getValue();
		}

		@Override
		public int hashCode() {
			return e.hashCode();
		}

		@Override
		public V setValue(V value) {
			verify.run();
			if (null == value)
				throw new NullPointerException();
			Logs.link(value, logkey.getXBean(), logkey.getVarname());
			V origin = e.setValue(value);
			myLog().afterPut(e.getKey(), origin, value);
			return origin;
		}
	}

	private final class EntrySet extends AbstractSet<Map.Entry<K, V>> {
		@Override
		public Iterator<Map.Entry<K, V>> iterator() {
			return newEntryIterator();
		}

		@Override
		public boolean contains(Object o) {
			return wrapped.entrySet().contains(o);
		}

		@Override
		public int size() {
			return wrapped.entrySet().size();
		}

		@Override
		public boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			LogMap.this.clear();
		}
	}

	private EntrySet esview;

	@Override
	public Set<Map.Entry<K, V>> entrySet() {
		return esview != null ? esview : (esview = new EntrySet());
	}

	@Override
	public V get(Object key) {
		return wrapped.get(key);
	}

	@Override
	public int hashCode() {
		return wrapped.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return wrapped.equals(obj);
	}

	@Override
	public boolean isEmpty() {
		return wrapped.isEmpty();
	}

	private Iterator<K> newKeyIterator() {
		return new WrapEntryIt<K>() {
			public K next() {
				return nextEntry().getKey();
			}
		};
	}

	private final class KeySet extends AbstractSet<K> {
		@Override
		public Iterator<K> iterator() {
			return LogMap.this.newKeyIterator();
		}

		@Override
		public int size() {
			return LogMap.this.size();
		}

		@Override
		public boolean contains(Object o) {
			return LogMap.this.containsKey(o);
		}

		@Override
		public boolean remove(Object o) {
			return LogMap.this.remove(o) != null;
		}

		@Override
		public void clear() {
			LogMap.this.clear();
		}
	}

	private KeySet ksview;

	@Override
	public Set<K> keySet() {
		return ksview != null ? ksview : (ksview = new KeySet());
	}

	@Override
	public V put(K key, V value) {
		verify.run();
		if (null == value || null == key)
			throw new NullPointerException();
		Logs.link(value, logkey.getXBean(), logkey.getVarname());
		V origin = wrapped.put(key, value);
		myLog().afterPut(key, origin, value);
		return origin;
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		verify.run();
		m.forEach((key, value) -> put(key, value));
	}

	@SuppressWarnings("unchecked")
	@Override
	public V remove(Object arg0) {
		verify.run();
		V v = wrapped.remove(arg0);
		if (null != v)
			myLog().afterRemove((K) arg0, v);
		return v;
	}

	@Override
	public int size() {
		return wrapped.size();
	}

	private Iterator<V> newValueIterator() {
		return new WrapEntryIt<V>() {
			public V next() {
				return nextEntry().getValue();
			}
		};
	}

	private final class Values extends AbstractCollection<V> {
		@Override
		public Iterator<V> iterator() {
			return LogMap.this.newValueIterator();
		}

		@Override
		public int size() {
			return LogMap.this.size();
		}

		@Override
		public boolean contains(Object o) {
			return LogMap.this.containsValue(o);
		}

		@Override
		public void clear() {
			LogMap.this.clear();
		}
	}

	private Values vsview;

	@Override
	public Collection<V> values() {
		return vsview != null ? vsview : (vsview = new Values());
	}

	@Override
	public String toString() {
		return wrapped.toString();
	}
}
