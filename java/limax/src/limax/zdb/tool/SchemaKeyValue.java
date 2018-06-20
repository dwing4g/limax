package limax.zdb.tool;

import java.util.LinkedHashMap;
import java.util.Map;

import limax.xmlgen.Type;

class SchemaKeyValue implements Schema {
	private final Schema keySchema;
	private final Schema valueSchema;

	public SchemaKeyValue(Type keyType, Type valueType) {
		this.keySchema = Schemas.of(keyType);
		this.valueSchema = Schemas.of(valueType);
	}

	public Schema keySchema() {
		return keySchema;
	}

	public Schema valueSchema() {
		return valueSchema;
	}

	public Map<String, Schema> entries() {
		Map<String, Schema> map = new LinkedHashMap<>();
		map.put("key", keySchema);
		map.put("value", valueSchema);
		return map;
	}

	@Override
	public DataKeyValue create() {
		return new DataKeyValue(this);
	}

	@Override
	public ConvertType diff(Schema t, boolean asKey) {
		if (t instanceof SchemaKeyValue) {
			SchemaKeyValue target = (SchemaKeyValue) t;
			ConvertType keyDiff = keySchema.diff(target.keySchema, true);
			ConvertType valueDiff = valueSchema.diff(target.valueSchema, asKey);
			if (keyDiff == ConvertType.MANUAL || valueDiff == ConvertType.MANUAL)
				return ConvertType.MANUAL;
			else if (keyDiff == ConvertType.MAYBE_AUTO || valueDiff == ConvertType.MAYBE_AUTO)
				return ConvertType.MAYBE_AUTO;
			else if (keyDiff == ConvertType.AUTO || valueDiff == ConvertType.AUTO)
				return ConvertType.AUTO;
			return ConvertType.SAME;
		}
		return ConvertType.MANUAL;
	}

	@Override
	public boolean isDynamic() {
		return keySchema.isDynamic() || valueSchema.isDynamic();
	}

	@Override
	public String toString() {
		return Schemas.str(entries(), "");
	}
}