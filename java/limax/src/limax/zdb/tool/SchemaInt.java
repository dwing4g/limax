package limax.zdb.tool;

class SchemaInt implements Schema {
	private final IntType type;

	public SchemaInt(IntType type) {
		this.type = type;
	}

	public IntType intType() {
		return type;
	}

	@Override
	public DataInt create() {
		return new DataInt(this);
	}

	@Override
	public ConvertType diff(Schema t, boolean asKey) {
		if (t instanceof SchemaInt) {
			SchemaInt target = (SchemaInt) t;
			if (type == target.type || (type == IntType.BOOLEAN && target.type == IntType.BYTE))
				return ConvertType.SAME;
			else if (type.size() < target.type.size())
				return ConvertType.AUTO;
		} else if (t instanceof SchemaFloat) {
			if (type != IntType.BOOLEAN)
				return ConvertType.MAYBE_AUTO;
		}
		return ConvertType.MANUAL;
	}

	@Override
	public boolean isDynamic() {
		return false;
	}

	@Override
	public String toString() {
		return type.toString().toLowerCase();
	}
}