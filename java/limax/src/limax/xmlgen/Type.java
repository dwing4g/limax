package limax.xmlgen;

import java.util.HashSet;
import java.util.Set;

import org.w3c.dom.Element;

public abstract class Type extends Naming implements Dependency {

	Type(Naming parent, Element self, String key) throws Exception {
		super(parent, self, key);
	}

	public Type(Naming parent, Element self) throws Exception {
		super(parent, self);
	}

	Type(Naming parent, String name) {
		super(parent, name);
	}

	Type(Naming parent) {
		super(parent, ""); // disable find
	}

	public Type(String name) {
		super(Naming.typeRoot, name);
		resolved();
	}

	private static Type find(Naming parent, String type) {
		if (type == null || type.isEmpty()) {
			return null;
		} else if (type.startsWith("any:")) {
			return new TypeAny(parent, type.substring(4));
		} else {
			Type t = Naming.typeRoot.search(new String[] { type }, Type.class);
			if (t != null)
				return t;
			return parent.search(type.split("\\."), Type.class);
		}
	}

	static Type resolve(Naming parent, String type, String key, String value) {
		Type r = find(parent, type);
		if (r != null)
			return r;
		Type keyType;
		Type valueType = find(parent, value);
		if (valueType == null)
			return null;
		switch (type) {
		case "list":
			return new TypeList(parent, valueType);
		case "vector":
			return new TypeVector(parent, valueType);
		case "set":
			return new TypeSet(parent, valueType);
		case "map":
			if ((keyType = find(parent, key)) != null)
				return new TypeMap(parent, keyType, valueType);
			break;
		}
		return null;
	}

	static {
		new TypeByte();
		new TypeShort();
		new TypeInt();
		new TypeLong();
		new TypeFloat();
		new TypeDouble();
		new TypeBinary();
		new TypeString();
		new TypeBoolean();
	}

	public abstract void accept(Visitor visitor);

	public boolean isConstType() {
		return !depends().stream().filter(t -> t != this).filter(t -> !t.isConstType()).findAny().isPresent();
	}

	public boolean isAny() {
		return depends().stream().filter(t -> t != this).filter(t -> t.isAny()).findAny().isPresent();
	}

	public boolean isJSONSerializable() {
		return !depends().stream().filter(t -> t != this).filter(t -> !t.isJSONSerializable()).findAny().isPresent();
	}

	@Override
	public void depends(Set<Type> incls) {
		incls.add(this);
	}

	public Set<Type> depends() {
		Set<Type> types = new HashSet<Type>();
		depends(types);
		return types;
	}
}
