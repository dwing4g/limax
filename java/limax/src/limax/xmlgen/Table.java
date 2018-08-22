package limax.xmlgen;

import java.util.Set;

import org.w3c.dom.Element;

import limax.util.ElementHelper;

public class Table extends Naming implements Dependency {
	private String key;
	private String value;

	private String lock = "";
	private String cacheCap = "";
	private boolean autoIncrement;
	private boolean memory;
	private String foreign = "";
	private String capacity = "";

	private int cacheCapValue;
	private Type keyType;
	private Type valueType;

	public Table(Zdb parent, Element self) throws Exception {
		super(parent, self);
		String name = getName();
		if (name.isEmpty())
			throw new RuntimeException("table name MUST NOT empty");
		if (!name.toLowerCase().equals(name))
			throw new RuntimeException("table name MUST BE lowercase");
		ElementHelper eh = new ElementHelper(self);
		lock = eh.getString("lock");
		key = eh.getString("key");
		value = eh.getString("value");
		memory = "MEMORY".equalsIgnoreCase(eh.getString("persistence"));
		autoIncrement = "true".equalsIgnoreCase(eh.getString("autoIncrement"));
		foreign = eh.getString("foreign");
		capacity = eh.getString("capacity");
		cacheCap = eh.getString("cacheCapacity");
		if (getName().equalsIgnoreCase("_Meta_"))
			throw new RuntimeException("Table name \"" + getName() + "\" is not permitted.");
		if (getName().equalsIgnoreCase("_Tables_"))
			throw new RuntimeException("Table name \"" + getName() + "\" is not permitted.");
	}

	public void initialize(Element self) {
		ElementHelper eh = new ElementHelper(self);
		capacity = eh.getString("capacity");
		eh.warnUnused("name");
	}

	public static final class Builder {
		Table table;

		public Builder(Zdb zdb, String name, String key, String value) {
			table = new Table(zdb, name);
			table.key = key;
			table.value = value;
		}

		public Table build() {
			return table;
		}

		public Builder memory(boolean memory) {
			table.memory = memory;
			return this;
		}

		public Builder autoIncrement(boolean autoIncrement) {
			table.autoIncrement = autoIncrement;
			return this;
		}

		public Builder lock(String lock) {
			table.lock = lock;
			return this;
		}

		public Builder cacheCap(String cacheCap) {
			table.cacheCap = cacheCap;
			return this;
		}

		public Builder foreign(String foreign) {
			table.foreign = foreign;
			return this;
		}

		public Builder capacity(String capacity) {
			table.capacity = capacity;
			return this;
		}
	}

	private Table(Zdb zdb, String name) {
		super(zdb, name);
	}

	@Override
	boolean resolve() {
		if (!super.resolve())
			return false;

		if ((keyType = Type.resolve(this, key, null, null)) == null)
			return false;
		if ((valueType = Type.resolve(this, value, null, null)) == null)
			return false;

		cacheCapValue = cacheCap.isEmpty() ? 10240 : Integer.parseInt(cacheCap);

		if (!keyType.isConstType())
			throw new RuntimeException("Key of table " + getName() + "MUST be constant type.");

		if (autoIncrement && !(keyType instanceof TypeLong))
			throw new RuntimeException("Key of table " + getName() + " not support autoIncrement");

		if (!(valueType instanceof Xbean) && !valueType.isConstType() && !(valueType instanceof TypeAny))
			throw new RuntimeException("Value of table " + getName() + " MUST be constant type or xbean or any");

		if (valueType.isAny() && !isMemory())
			throw new RuntimeException("Value of table " + getName() + " contains field with ANY type");
		return true;
	}

	@Override
	public void depends(Set<Type> types) {
		keyType.depends(types);
		valueType.depends(types);
	}

	public String getKey() {
		return key;
	}

	public String getValue() {
		return value;
	}

	public String getLock() {
		return lock;
	}

	public String getCapacity() {
		return capacity;
	}

	public String getForeign() {
		return foreign;
	}

	public String getCacheCap() {
		return cacheCap;
	}

	public Type getKeyType() {
		return keyType;
	}

	public Type getValueType() {
		return valueType;
	}

	public boolean isAutoIncrement() {
		return autoIncrement;
	}

	public boolean isMemory() {
		return memory;
	}

	public int getCacheCapValue() {
		return cacheCapValue;
	}
}
