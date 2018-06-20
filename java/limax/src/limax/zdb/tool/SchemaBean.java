package limax.zdb.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import limax.xmlgen.Cbean;
import limax.xmlgen.Type;
import limax.xmlgen.Xbean;

class SchemaBean implements Schema {
	private final Map<String, Schema> entries = new LinkedHashMap<>();
	private final Map<String, Schema> dynamicEntries = new LinkedHashMap<>();
	private final Type type;
	private final boolean dynamic;

	public SchemaBean(Cbean bean) {
		type = bean;
		bean.getVariables().forEach(v -> entries.put(v.getName(), Schemas.of(v.getType())));
		dynamic = false;
	}

	public SchemaBean(Xbean bean) {
		type = bean;
		bean.getStaticVariables().forEach(v -> entries.put(v.getName(), Schemas.of(v.getType())));
		bean.getDynamicVariables().forEach(v -> dynamicEntries.put(v.getName(), Schemas.of(v.getType())));
		dynamic = bean.isDynamic();
	}

	public Type type() {
		return type;
	}

	public String typeName() {
		return type.getName();
	}

	public Map<String, Schema> entries() {
		return entries;
	}

	public Map<String, Schema> dynamicEntries() {
		return dynamicEntries;
	}

	@Override
	public DataBean create() {
		return new DataBean(this);
	}

	@Override
	public ConvertType diff(Schema t, boolean asKey) {
		if (!(t instanceof SchemaBean))
			return ConvertType.MANUAL;
		SchemaBean target = (SchemaBean) t;
		if (!target.type.getName().equals(type.getName()))
			return ConvertType.MANUAL;
		Map<String, Schema> targetEntries = new HashMap<>(target.entries);
		int remove = 0;
		int auto = 0;
		int mayBeAuto = 0;
		for (Map.Entry<String, Schema> e : entries.entrySet()) {
			Schema child = e.getValue();
			Schema targetChild = targetEntries.remove(e.getKey());
			if (targetChild == null) {
				if (asKey)
					return ConvertType.MANUAL;
				remove++;
			} else {
				switch (child.diff(targetChild, asKey)) {
				case SAME:
					break;
				case AUTO:
					auto++;
					break;
				case MAYBE_AUTO:
					mayBeAuto++;
					break;
				case MANUAL:
					return ConvertType.MANUAL;
				}
			}
		}
		int add = targetEntries.size();
		if (add > 0 || mayBeAuto > 0)
			return ConvertType.MAYBE_AUTO;
		else if (remove > 0 || auto > 0)
			return ConvertType.AUTO;
		else if (new ArrayList<>(entries.keySet()).equals(new ArrayList<>(target.entries.keySet())))
			return ConvertType.SAME;
		else
			return ConvertType.AUTO;
	}

	@Override
	public boolean isDynamic() {
		return dynamic || entries.values().stream().filter(Schema::isDynamic).findAny().isPresent();
	}

	@Override
	public String toString() {
		return Schemas.str(entries(), "");
	}

}