package limax.zdb.tool;

import limax.xmlgen.Type;

class SchemaCollection implements Schema {
	private final String typeName;
	private final Schema elementSchema;
	private final boolean isSet;

	public SchemaCollection(String typeName, Type elementType, boolean isSet) {
		this.typeName = typeName;
		this.elementSchema = Schemas.of(elementType);
		this.isSet = isSet;
	}

	public SchemaCollection(String typeName, Type keyType, Type valueType) {
		this.typeName = typeName;
		this.elementSchema = new SchemaKeyValue(keyType, valueType);
		this.isSet = false;
	}

	public String typeName() {
		return typeName;
	}

	public Schema elementSchema() {
		return elementSchema;
	}

	@Override
	public DataCollection create() {
		return new DataCollection(this);
	}

	@Override
	public ConvertType diff(Schema t, boolean asKey) {
		if (t instanceof SchemaCollection) {
			return elementSchema.diff(((SchemaCollection) t).elementSchema, isSet || asKey);
		}
		return ConvertType.MANUAL;
	}

	@Override
	public boolean isDynamic() {
		return elementSchema.isDynamic();
	}
}