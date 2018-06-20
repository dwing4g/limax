package limax.xmlgen;

public final class TypeBinary extends Type {

	TypeBinary() {
		super("binary");
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
		return false;
	}

	@Override
	public boolean isAny() {
		return false;
	}

	@Override
	public boolean isJSONSerializable() {
		return false;
	}
}
