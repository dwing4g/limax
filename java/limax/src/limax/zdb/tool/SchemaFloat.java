package limax.zdb.tool;

class SchemaFloat implements Schema {
	private final boolean isDouble;

	SchemaFloat(boolean isDouble) {
		this.isDouble = isDouble;
	}

	@Override
	public DataFloat create() {
		return new DataFloat(this);
	}

	public boolean isDouble() {
		return isDouble;
	}

	@Override
	public ConvertType diff(Schema t, boolean asKey) {
		if (t instanceof SchemaFloat) {
			SchemaFloat o = (SchemaFloat) t;
			if (isDouble == o.isDouble)
				return ConvertType.SAME;
			if (!isDouble)
				return ConvertType.AUTO;
		}
		return ConvertType.MANUAL;
	}

	@Override
	public boolean isDynamic() {
		return false;
	}

	@Override
	public String toString() {
		return isDouble ? "double" : "float";
	}
}