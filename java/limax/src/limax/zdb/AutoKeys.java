package limax.zdb;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import limax.codec.MarshalException;
import limax.codec.OctetsStream;

final class AutoKeys {
	private final int localInitValue;
	private final int localStep;
	private final Map<AutoKey, AutoKey> map = new HashMap<>();
	private final AtomicBoolean dirty = new AtomicBoolean();

	AutoKey getAutoKey(String name) {
		AutoKey tmp = new AutoKey(name, localInitValue, localStep);
		AutoKey found = map.get(tmp);
		if (null != found)
			return found;
		add(tmp);
		dirty.set(true);
		return tmp;
	}

	@Override
	public String toString() {
		return Arrays.toString(map.keySet().toArray());
	}

	OctetsStream encodeValue() {
		if (!dirty.compareAndSet(true, false))
			return null;
		OctetsStream os = new OctetsStream().marshal_size(map.size());
		for (AutoKey autoKey : map.keySet())
			autoKey.marshal(os);
		return os;
	}

	private void add(AutoKey ak) {
		if (null != map.put(ak, ak))
			throw new IllegalStateException("Duplicate AutoKey Found");
	}

	void remove(Set<String> unusedTables) {
		if (unusedTables.isEmpty())
			return;
		for (Iterator<AutoKey> it = map.keySet().iterator(); it.hasNext();) {
			AutoKey autoKey = it.next();
			if (unusedTables.contains(autoKey.name))
				it.remove();
		}
		dirty.set(true);
	}

	AutoKeys(OctetsStream os, int localInitValue, int localStep) {
		this.localInitValue = localInitValue;
		this.localStep = localStep;
		if (os == null)
			return;
		try {
			for (int size = os.unmarshal_size(); size > 0; size--)
				add(new AutoKey(os));
		} catch (MarshalException e) {
			throw new IllegalStateException(e);
		}
	}

	class AutoKey {
		private final String name;
		private final int initValue;
		private final int step;
		private long current;

		private long update(long value) {
			dirty.set(true);
			if (Transaction.current().duration() != null)
				Transaction.current().duration().updateAutoKey(name, value);
			return current = value;
		}

		void recover(long key) {
			synchronized (this) {
				dirty.set(true);
				current = key;
			}
		}

		void accept(Long key) {
			if ((key % step) != initValue || key <= 0)
				throw new IllegalArgumentException("invalid autokey " + key);
			synchronized (this) {
				if (key > current)
					update(key);
			}
		}

		Long next() {
			if (Transaction.current() == null)
				throw new XError("Not in transaction");
			synchronized (this) {
				long tmp = current + step;
				if (tmp <= 0)
					throw new IllegalStateException("autokey expired!");
				return update(tmp);
			}
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof AutoKey) {
				AutoKey o = (AutoKey) obj;
				return initValue == o.initValue && name.equals(o.name);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return initValue ^ name.hashCode();
		}

		@Override
		public String toString() {
			return "(" + name + "," + initValue + "," + step + "," + current + ")";
		}

		private OctetsStream marshal(OctetsStream os) {
			os.marshal(name);
			os.marshal(initValue);
			os.marshal(step);
			synchronized (this) {
				os.marshal(current);
			}
			return os;
		}

		private AutoKey(String name, int initValue, int step) {
			if (initValue < 0 || initValue >= step)
				throw new IllegalArgumentException("AutoKey Invalid initValue=" + initValue);
			this.name = name;
			this.initValue = initValue;
			this.step = step;
			this.current = initValue;
		}

		private AutoKey(OctetsStream os) throws MarshalException {
			this.name = os.unmarshal_String();
			this.initValue = os.unmarshal_int();
			this.step = os.unmarshal_int();
			this.current = os.unmarshal_long();
		}
	}
}
