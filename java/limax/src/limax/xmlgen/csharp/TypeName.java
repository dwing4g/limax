package limax.xmlgen.csharp;

import limax.xmlgen.Bean;
import limax.xmlgen.Cbean;
import limax.xmlgen.Type;
import limax.xmlgen.TypeAny;
import limax.xmlgen.TypeBinary;
import limax.xmlgen.TypeBoolean;
import limax.xmlgen.TypeByte;
import limax.xmlgen.TypeDouble;
import limax.xmlgen.TypeFloat;
import limax.xmlgen.TypeInt;
import limax.xmlgen.TypeList;
import limax.xmlgen.TypeLong;
import limax.xmlgen.TypeMap;
import limax.xmlgen.TypeSet;
import limax.xmlgen.TypeShort;
import limax.xmlgen.TypeString;
import limax.xmlgen.TypeVector;
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;

class TypeName implements Visitor {
	private String name;

	public static String getName(Type type) {
		TypeName visitor = new TypeName();
		type.accept(visitor);
		return visitor.name;
	}

	public String getName() {
		return name;
	}

	@Override
	public void visit(Bean type) {
		name = type.getFullName();
	}

	@Override
	public void visit(TypeByte type) {
		name = "byte";
	}

	@Override
	public void visit(TypeInt type) {
		name = "int";
	}

	@Override
	public void visit(TypeShort type) {
		name = "short";
	}

	@Override
	public void visit(TypeLong type) {
		name = "long";
	}

	@Override
	public void visit(TypeBinary type) {
		name = "limax.codec.Octets";
	}

	@Override
	public void visit(TypeString type) {
		name = "string";
	}

	@Override
	public void visit(TypeList type) {
		name = "LinkedList<" + getName(type.getValueType()) + ">";
	}

	@Override
	public void visit(TypeVector type) {
		name = "List<" + getName(type.getValueType()) + ">";
	}

	@Override
	public void visit(TypeSet type) {
		name = "HashSet<" + getName(type.getValueType()) + ">";
	}

	@Override
	public void visit(TypeMap type) {
		name = "Dictionary<" + getName(type.getKeyType()) + ", " + getName(type.getValueType()) + ">";
	}

	@Override
	public void visit(TypeFloat type) {
		name = "float";
	}

	@Override
	public void visit(TypeDouble type) {
		name = "double";
	}

	@Override
	public void visit(Cbean type) {
		name = type.getFullName();
	}

	@Override
	public void visit(Xbean type) {
		name = type.getFullName();
	}

	@Override
	public void visit(TypeBoolean type) {
		name = "bool";
	}

	@Override
	public void visit(TypeAny type) {
		throw new UnsupportedOperationException();
	}

}
