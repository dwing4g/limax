package limax.xmlgen;

import java.util.Set;

public final class TypeVector extends Type {
	private final Type valueType;

	TypeVector(Naming parent, Type valueType) {
		super(parent);
		this.valueType = valueType;
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
		return valueType.isAny();
	}

	@Override
	public boolean isJSONSerializable() {
		return valueType.isJSONSerializable();
	}

	@Override
	public void depends(Set<Type> incls) {
		incls.add(this);
		valueType.depends(incls);
	}

	public Type getValueType() {
		return valueType;
	}
}
