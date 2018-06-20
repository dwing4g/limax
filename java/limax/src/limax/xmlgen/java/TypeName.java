package limax.xmlgen.java;

import limax.xmlgen.Bean;
import limax.xmlgen.Cbean;
import limax.xmlgen.Main;
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

	public static String getName(Type type) {
		return getName(type, Purpose.TYPE);
	}

	public static String getBoxingName(Type type) {
		return getName(type, Purpose.BOXING);
	}

	public static String getGetterName(Type type) {
		return getName(type, Purpose.GETTER);
	}

	static enum Purpose {
		TYPE, BOXING, GETTER, CONTAINER_ITEM
	}

	static String getName(Type type, Purpose purpose) {
		TypeName visitor = new TypeName(purpose);
		type.accept(visitor);
		return visitor.getName();
	}

	private String name;
	private Purpose purpose;

	public String getName() {
		return name;
	}

	public TypeName(Purpose purpose) {
		this.purpose = purpose;
	}

	private boolean box() {
		return purpose == Purpose.BOXING || purpose == Purpose.CONTAINER_ITEM;
	}

	private boolean type() {
		return purpose == Purpose.TYPE || purpose == Purpose.CONTAINER_ITEM;
	}

	@Override
	public void visit(TypeBoolean type) {
		name = box() ? "Boolean" : "boolean";
	}

	@Override
	public void visit(TypeByte type) {
		name = box() ? "Byte" : "byte";
	}

	@Override
	public void visit(TypeInt type) {
		name = box() ? "Integer" : "int";
	}

	@Override
	public void visit(TypeShort type) {
		name = box() ? "Short" : "short";
	}

	@Override
	public void visit(TypeLong type) {
		name = box() ? "Long" : "long";
	}

	@Override
	public void visit(TypeFloat type) {
		name = box() ? "Float" : "float";
	}

	@Override
	public void visit(TypeDouble type) {
		name = box() ? "Double" : "double";
	}

	@Override
	public void visit(TypeBinary type) {
		if (Main.isMakingZdb) {
			if (box())
				throw new UnsupportedOperationException();
			else
				name = "byte[]";
		} else {
			name = "limax.codec.Octets";
		}
	}

	@Override
	public void visit(TypeString type) {
		name = "String";
	}

	private void set(String basename, String typename, Type value) {
		String vn = TypeName.getName(value, Purpose.BOXING);
		String tn = type() ? typename : basename;
		name = "java.util." + tn + "<" + vn + ">";
	}

	@Override
	public void visit(TypeList type) {
		set("List", "LinkedList", type.getValueType());
	}

	@Override
	public void visit(TypeVector type) {
		set("List", "ArrayList", type.getValueType());
	}

	@Override
	public void visit(TypeSet type) {
		String vn = TypeName.getBoxingName(type.getValueType());
		String tn;
		if (type()) {
			if (Main.isMakingNet)
				tn = "java.util.HashSet";
			else if (Main.isMakingConverter)
				tn = "java.util.HashSet";
			else
				tn = "limax.zdb.SetX";
		} else {
			tn = "java.util.Set";
		}
		name = tn + "<" + vn + ">";
	}

	private void set(String basename, String typename, Type key, Type value) {
		String kn = TypeName.getBoxingName(key);
		String vn = TypeName.getBoxingName(value);
		String tn = type() ? typename : basename;
		name = "java.util." + tn + "<" + kn + ", " + vn + ">";
	}

	@Override
	public void visit(TypeMap type) {
		set("Map", "HashMap", type.getKeyType(), type.getValueType());
	}

	@Override
	public void visit(Bean type) {
		name = type.getFullName();
	}

	@Override
	public void visit(Cbean type) {
		name = Main.isMakingConverter ? type.getName() : type.getFullName();
	}

	@Override
	public void visit(Xbean type) {
		name = Main.isMakingConverter ? type.getName() : type.getFullName();
	}

	@Override
	public void visit(TypeAny type) {
		name = type.getTypeName();
	}
}