package limax.xmlgen;

public class TypeBoolean extends Type {

	TypeBoolean() {
		super("boolean");
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
