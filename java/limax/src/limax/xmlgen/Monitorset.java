package limax.xmlgen;

import java.util.List;

import org.w3c.dom.Element;

import limax.util.ElementHelper;

public final class Monitorset extends Naming {

	private boolean supportTransaction = true;
	private String mapKeyType = "";
	private String tableName;
	private Type typeMapKeyType = null;

	public Monitorset(Project parent, Element self) throws Exception {
		super(parent, self);
		initialize(self);
	}

	public Monitorset(Namespace parent, Element self) throws Exception {
		super(parent, self);
		initialize(self);
	}

	private void initialize(Element self) {
		ElementHelper eh = new ElementHelper(self);
		supportTransaction = eh.getBoolean("supportTransaction", true);
		mapKeyType = eh.getString("mapKeyType");
		tableName = eh.getString("tableName", getName()).toLowerCase();
		eh.warnUnused("name");
	}

	public String getFirstName() {
		StringBuilder sb = new StringBuilder();
		for (Naming p = getParent(); p != getRoot(); p = p.getParent())
			sb.insert(0, p.getName()).insert(0, ".");
		return sb.deleteCharAt(0).toString();
	}

	public String getFullName() {
		return getFirstName() + "." + getName();
	}

	public String getTableName() {
		return tableName;
	}

	public List<Monitor> getMonitors() {
		return getDescendants(Monitor.class);
	}

	public List<Key> getKeys() {
		return getDescendants(Key.class);
	}

	public boolean isSupportTransaction() {
		return supportTransaction;
	}

	@Override
	boolean resolve() {
		if (!mapKeyType.isEmpty()) {
			typeMapKeyType = Type.resolve(this, mapKeyType, null, null);
			if (null == typeMapKeyType)
				throw new RuntimeException("bad cacheKeysType = \"" + mapKeyType + "\"");
			if (!typeMapKeyType.isConstType() || (typeMapKeyType instanceof Cbean))
				throw new RuntimeException("Counterset " + getFullName() + " cacheKeysType " + typeMapKeyType.getName()
						+ " is not const type.");
		}
		return super.resolve();
	}

	public Type getMapKeyType() {
		return typeMapKeyType;
	}
}
