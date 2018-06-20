package limax.xmlgen.cpp;

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

class TypeReferenceName implements Visitor {
	private String name;

	public static String getName(Type type) {
		TypeReferenceName visitor = new TypeReferenceName();
		type.accept(visitor);
		return visitor.name;
	}

	public String getName() {
		return name;
	}

	@Override
	public void visit(Bean type) {
		name = "const class " + type.getFullName().replace(".", "::") + "&";
	}

	@Override
	public void visit(TypeByte type) {
		name = "int8_t";
	}

	@Override
	public void visit(TypeInt type) {
		name = "int32_t";
	}

	@Override
	public void visit(TypeShort type) {
		name = "int16_t";
	}

	@Override
	public void visit(TypeLong type) {
		name = "int64_t";
	}

	@Override
	public void visit(TypeBinary type) {
		name = "const limax::Octets&";
	}

	@Override
	public void visit(TypeString type) {
		name = "const std::string&";
	}

	@Override
	public void visit(TypeList type) {
		String valuename = BoxingName.getName(type.getValueType());
		name = "const std::list<" + valuename + ">&";
	}

	@Override
	public void visit(TypeVector type) {
		String valuename = BoxingName.getName(type.getValueType());
		name = "const std::vector<" + valuename + ">&";
	}

	@Override
	public void visit(TypeSet type) {
		final Type valuetype = type.getValueType();
		String valuename = BoxingName.getName(valuetype);
		name = "const limax::hashset<" + valuename + ">&";
	}

	@Override
	public void visit(TypeMap type) {
		String keyname = BoxingName.getName(type.getKeyType());
		String valuename = BoxingName.getName(type.getValueType());
		name = "const limax::hashmap<" + keyname + ", " + valuename + ">&";
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
		name = "const class " + type.getFullName().replace(".", "::") + "&";
	}

	@Override
	public void visit(Xbean type) {
		name = "const class " + type.getFullName().replace(".", "::") + "&";
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
