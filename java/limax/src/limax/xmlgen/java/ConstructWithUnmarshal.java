package limax.xmlgen.java;

import java.io.PrintStream;

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

public class ConstructWithUnmarshal implements Visitor {
	private final String varname;
	private final PrintStream ps;
	private final String prefix;

	private ConstructWithUnmarshal(String varname, PrintStream ps, String prefix) {
		this.varname = varname;
		this.ps = ps;
		this.prefix = prefix;
	}

	public static void make(Type type, String varname, PrintStream ps, String prefix) {
		type.accept(new ConstructWithUnmarshal(varname, ps, prefix));
	}

	static void make(String typename, String varname, PrintStream ps, String prefix) {
		ps.println(prefix + typename + " " + varname + " = new " + typename + "();");
		ps.println(prefix + varname + ".unmarshal(_os_);");
	}

	@Override
	public void visit(TypeBoolean type) {
		ps.println(prefix + "boolean " + varname + " = _os_.unmarshal_boolean();");
	}

	@Override
	public void visit(TypeByte type) {
		ps.println(prefix + "byte " + varname + " = _os_.unmarshal_byte();");
	}

	@Override
	public void visit(TypeShort type) {
		ps.println(prefix + "short " + varname + " = _os_.unmarshal_short();");
	}

	@Override
	public void visit(TypeInt type) {
		ps.println(prefix + "int " + varname + " = _os_.unmarshal_int();");
	}

	@Override
	public void visit(TypeLong type) {
		ps.println(prefix + "long " + varname + " = _os_.unmarshal_long();");
	}

	@Override
	public void visit(TypeFloat type) {
		ps.println(prefix + "float " + varname + " = _os_.unmarshal_float();");
	}

	@Override
	public void visit(TypeDouble type) {
		ps.println(prefix + "double " + varname + " = _os_.unmarshal_double();");
	}

	@Override
	public void visit(TypeBinary type) {
		ps.println(prefix + "limax.codec.Octets " + varname + " = _os_.unmarshal_Octets();");
	}

	@Override
	public void visit(TypeString type) {
		ps.println(prefix + "String " + varname + " = _os_.unmarshal_String();");
	}

	private void newVariable(Type type) {
		Define.make(type, varname, ps, prefix);
	}

	private void unmarshalContainer(Type valueType) {
		ps.println(prefix + "for(int _i_ = _os_.unmarshal_size(); _i_ > 0; --_i_) {");
		valueType.accept(new ConstructWithUnmarshal("_v_", ps, prefix + "	"));
		ps.println(prefix + "	" + varname + ".add(_v_);");
		ps.println(prefix + "}");
	}

	private void unmarshalContainer(Type keytype, Type valuetype) {
		ps.println(prefix + "for(int _i_ = _os_.unmarshal_size(); _i_ > 0; --_i_) {");
		keytype.accept(new ConstructWithUnmarshal("_k_", ps, prefix + "	"));
		valuetype.accept(new ConstructWithUnmarshal("_v_", ps, prefix + "	"));
		ps.println(prefix + "	" + varname + ".put(_k_, _v_);");
		ps.println(prefix + "}");
	}

	@Override
	public void visit(TypeList type) {
		newVariable(type);
		unmarshalContainer(type.getValueType());
	}

	@Override
	public void visit(TypeSet type) {
		newVariable(type);
		unmarshalContainer(type.getValueType());
	}

	@Override
	public void visit(TypeVector type) {
		newVariable(type);
		unmarshalContainer(type.getValueType());
	}

	@Override
	public void visit(TypeMap type) {
		newVariable(type);
		unmarshalContainer(type.getKeyType(), type.getValueType());
	}

	@Override
	public void visit(Bean type) {
		newVariable(type);
		ps.println(prefix + varname + ".unmarshal(_os_);");
	}

	@Override
	public void visit(Cbean type) {
		newVariable(type);
		ps.println(prefix + varname + ".unmarshal(_os_);");
	}

	@Override
	public void visit(Xbean type) {
		newVariable(type);
		ps.println(prefix + varname + ".unmarshal(_os_);");
	}

	@Override
	public void visit(TypeAny type) {
		throw new UnsupportedOperationException();
	}

}
