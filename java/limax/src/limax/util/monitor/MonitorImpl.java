package limax.util.monitor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;

import limax.provider.ProcedureHelper;
import limax.util.MBeans;
import limax.util.Trace;
import limax.zdb.Transaction;

class MonitorImpl {
	private MonitorImpl() {
	}

	private interface Counter {
		void increment();

		void increment(long delta);

		void set(long value);

		long get();
	}

	private static class LongCounter implements Counter {
		private final AtomicLong value = new AtomicLong();

		LongCounter() {
		}

		@Override
		public void increment() {
			value.incrementAndGet();
		}

		@Override
		public void increment(long delta) {
			value.addAndGet(delta);
		}

		@Override
		public void set(long value) {
			this.value.set(value);
		}

		@Override
		public long get() {
			return value.get();
		}
	}

	private static final Counter hollowCounterInstance = new Counter() {

		@Override
		public void increment() {
		}

		@Override
		public void increment(long delta) {
		}

		@Override
		public void set(long value) {
		}

		@Override
		public long get() {
			return 0;
		}
	};

	private interface CounterGroup {
		Counter get(String name);
	}

	private static class MBeanGroup implements CounterGroup, DynamicMBean {
		private final Map<String, Counter> counters;
		private final String objname;
		private final String className;
		private final String description;

		public MBeanGroup(String className, String objname, Collection<KeyInfo> keys, String... counternames) {
			this.className = className;
			this.counters = Arrays.stream(counternames)
					.collect(Collectors.toMap(Function.identity(), i -> new LongCounter()));
			MBeans.register(MBeans.root(), this, objname);
			this.objname = objname;
			this.description = this.className + (keys.isEmpty() ? " Monitor Group" : " Monitor Set");
		}

		@Override
		public Object getAttribute(String name) {
			return get(name);
		}

		@Override
		public AttributeList getAttributes(String[] attributes) {
			return new AttributeList(Arrays.stream(attributes).map(name -> new Attribute(name, get(name).get()))
					.collect(Collectors.toList()));
		}

		@Override
		public Counter get(String name) {
			final Counter c = counters.get(name);
			if (null != c)
				return c;
			if (Trace.isErrorEnabled())
				Trace.error("counter \"" + objname + "\" unknown name = \"" + name + "\"");
			return hollowCounterInstance;
		}

		@Override
		public void setAttribute(Attribute attribute) {
		}

		@Override
		public AttributeList setAttributes(AttributeList attributes) {
			return null;
		}

		@Override
		public Object invoke(String actionName, Object params[], String signature[]) {
			return null;
		}

		@Override
		public MBeanInfo getMBeanInfo() {
			return new MBeanInfo(className, description,
					counters.keySet().stream()
							.map(i -> new MBeanAttributeInfo(i, "java.lang.Long", i + " counter", true, false, false))
							.toArray(i -> new MBeanAttributeInfo[i]),
					null, null, null);
		}

		@Override
		public String toString() {
			return className;
		}
	}

	private static class GroupImpl implements Group {
		private final CounterGroup group;

		GroupImpl(Class<?> cls, String... counternames) {
			final String clsname = cls.getName();
			group = new MBeanGroup(clsname, clsname + ":$=monitor", Collections.emptyList(), counternames);
		}

		@Override
		public void increment(String name) {
			group.get(name).increment();
		}

		@Override
		public void increment(String name, long delta) {
			group.get(name).increment(delta);
		}

		@Override
		public void set(String name, long value) {
			group.get(name).set(value);
		}

		@Override
		public String toString() {
			return group.toString();
		}

		@Override
		public long get(String name) {
			return group.get(name).get();
		}
	}

	private static class GroupTransactionImpl extends GroupImpl {
		GroupTransactionImpl(Class<?> cls, String... counternames) {
			super(cls, counternames);
		}

		@Override
		public void increment(String name) {
			if (Transaction.isActive())
				ProcedureHelper.executeWhileCommit(() -> super.increment(name));
			else
				super.increment(name);
		}

		@Override
		public void increment(String name, long delta) {
			if (Transaction.isActive())
				ProcedureHelper.executeWhileCommit(() -> super.increment(name, delta));
			else
				super.increment(name, delta);
		}

		@Override
		public void set(String name, long value) {
			if (Transaction.isActive())
				ProcedureHelper.executeWhileCommit(() -> super.set(name, value));
			else
				super.set(name, value);
		}
	}

	private static class SetImpl<Key extends GroupKeys> implements MonitorSet<Key> {
		private final HashMap<Key, CounterGroup> lockmap = new HashMap<>();
		private volatile Map<Key, CounterGroup> map = lockmap;

		private final String[] counternames;
		private final String counterClassName;

		public SetImpl(Class<?> cls, String... counternames) {
			this.counterClassName = cls.getName();
			this.counternames = counternames;
		}

		private static String quote(String s) {
			StringBuilder sb = new StringBuilder();
			for (char c : s.toCharArray())
				switch (c) {
				case '\\':
					sb.append("\\\\");
					break;
				case '\n':
					sb.append("\\n");
					break;
				case '"':
				case '*':
				case '?':
					sb.append('\\').append(c);
					break;
				default:
					sb.append(c);
				}
			if (sb.length() == s.length())
				return s;
			return sb.insert(0, '"').append('"').toString();
		}

		private String makeMBeanObjectName(Key key) {
			return counterClassName + ":" + key.getKeys().stream()
					.map(i -> i.getName() + "=" + quote(i.getValue().toString())).collect(Collectors.joining(","));
		}

		@SuppressWarnings("unchecked")
		private Counter counter(String name, Key key) {
			CounterGroup group = map.get(key);
			if (null == group) {
				synchronized (lockmap) {
					group = lockmap.get(key);
					if (null == group) {
						group = new MBeanGroup(counterClassName, makeMBeanObjectName(key), key.getKeys(), counternames);
						lockmap.put(key, group);
						map = (Map<Key, CounterGroup>) lockmap.clone();
					}
				}
			}
			return group.get(name);
		}

		@Override
		public void increment(String name, Key key) {
			counter(name, key).increment();
		}

		@Override
		public void increment(String name, Key key, long delta) {
			counter(name, key).increment(delta);
		}

		@Override
		public void set(String name, Key key, long value) {
			counter(name, key).set(value);
		}

		@Override
		public long get(String name, Key key) {
			final CounterGroup group = map.get(key);
			return null == group ? 0L : group.get(name).get();
		}

		@Override
		public String toString() {
			return counterClassName;
		}

	}

	private static class SetTransactionImpl<Key extends GroupKeys> extends SetImpl<Key> {
		public SetTransactionImpl(Class<?> cls, String... counternames) {
			super(cls, counternames);
		}

		@Override
		public void increment(String name, Key key) {
			if (Transaction.isActive())
				ProcedureHelper.executeWhileCommit(() -> super.increment(name, key));
			else
				super.increment(name, key);
		}

		@Override
		public void increment(String name, Key key, long delta) {
			if (Transaction.isActive())
				ProcedureHelper.executeWhileCommit(() -> super.increment(name, key, delta));
			else
				super.increment(name, key, delta);
		}

		@Override
		public void set(String name, Key key, long value) {
			if (Transaction.isActive())
				ProcedureHelper.executeWhileCommit(() -> super.set(name, key, value));
			else
				super.set(name, key, value);
		}
	}

	private static class MapKeyImpl<MapKey, RawKey extends GroupKeys> implements MapKeySet<MapKey, RawKey> {
		private final MonitorSet<RawKey> impl;
		private final Map<MapKey, RawKey> cache = new ConcurrentHashMap<>();

		public MapKeyImpl(MonitorSet<RawKey> impl) {
			this.impl = impl;
		}

		@Override
		public void mapKey(MapKey k, RawKey v) {
			cache.put(k, v);
		}

		private RawKey getMappedKey(MapKey k) {
			RawKey v = cache.get(k);
			if (null == v && Trace.isErrorEnabled())
				Trace.error("counter \"" + impl.toString() + "\" unmapped key \"" + k.toString() + "\"");
			return v;
		}

		@Override
		public void increment(String name, RawKey key) {
			if (null != key)
				impl.increment(name, key);
		}

		@Override
		public void increment(String name, RawKey key, long delta) {
			if (null != key)
				impl.increment(name, key, delta);
		}

		@Override
		public void set(String name, RawKey key, long value) {
			if (null != key)
				impl.set(name, key, value);
		}

		@Override
		public void increment(String name, MapKey key) {
			increment(name, getMappedKey(key));
		}

		@Override
		public void increment(String name, MapKey key, long delta) {
			increment(name, getMappedKey(key), delta);
		}

		@Override
		public void set(String name, MapKey key, long value) {
			set(name, getMappedKey(key), value);
		}

		@Override
		public long get(String name, RawKey key) {
			return null != key ? impl.get(name, key) : 0;
		}

	}

	public static Group createGroup(Class<?> cls, boolean supportTransaction, String... counternames) {
		return supportTransaction ? new GroupTransactionImpl(cls, counternames) : new GroupImpl(cls, counternames);
	}

	public static <Key extends GroupKeys> MonitorSet<Key> createSet(Class<?> cls, boolean supportTransaction,
			String... counternames) {
		return supportTransaction ? new SetTransactionImpl<Key>(cls, counternames)
				: new SetImpl<Key>(cls, counternames);
	}

	public static <MapKey, RawKey extends GroupKeys> MapKeySet<MapKey, RawKey> createMapKeySet(Class<?> cls,
			boolean supportTransaction, String... counternames) {
		return new MapKeyImpl<MapKey, RawKey>(createSet(cls, supportTransaction, counternames));
	}
}
