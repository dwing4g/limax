package limax.xmlgen;

public final class TypeInt extends Type {

	TypeInt() {
		super("int");
	}

	@Override
	public boolean resolve() {
		return true;
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visit(this);
	}

	@Override
	public boolean isConstType() {
		return true;
	}

	@Override
	public boolean isAny() {
		return false;
	}

	@Override
	public boolean isJSONSerializable() {
		return true;
	}
}
