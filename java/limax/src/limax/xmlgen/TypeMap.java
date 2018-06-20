package limax.xmlgen;

import java.util.Set;

public final class TypeMap extends Type {
	private final Type keyType;
	private final Type valueType;
	private final TypeSet keySetType;

	TypeMap(Naming parent, Type keyType, Type valueType) {
		super(parent);
		this.keyType = keyType;
		this.valueType = valueType;
		this.keySetType = new TypeSet(parent, keyType);
	}

	@Override
	boolean resolve() {
		if (!super.resolve())
			return false;

		if (!keyType.isConstType())
			throw new RuntimeException("map.key must be constant type");
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
		return valueType.isAny() || keyType.isAny();
	}

	@Override
	public boolean isJSONSerializable() {
		return valueType.isJSONSerializable();
	}

	@Override
	public void depends(Set<Type> incls) {
		incls.add(this);
		keyType.depends(incls);
		valueType.depends(incls);
	}

	public Type getValueType() {
		return valueType;
	}

	public Type getKeyType() {
		return keyType;
	}

	public TypeSet getKeySetType() {
		return keySetType;
	}
}
