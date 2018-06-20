package limax.zdb.tool;

interface Schema {
	Data create();

	ConvertType diff(Schema target, boolean asKey);

	boolean isDynamic();

	static boolean equals(Schema source, Schema target) {
		return source.diff(target, true) == ConvertType.SAME;
	}
}
