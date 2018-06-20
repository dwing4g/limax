package limax.xmlgen.java;

import java.io.PrintStream;

import limax.util.StringUtils;
import limax.xmlgen.Bean;
import limax.xmlgen.Cbean;
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
import limax.xmlgen.Variable;
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;

class VarSetter implements Visitor {

	static void make(Variable var, PrintStream ps, String prefix) {
		var.getType().accept(new VarSetter(var, ps, prefix));
	}

	private Variable var;
	private PrintStream ps;
	private String prefix;
	private String varname;
	private String this_varname;
	private String Varname;

	private VarSetter(Variable var, PrintStream ps, String prefix) {
		this.var = var;
		this.ps = ps;
		this.prefix = prefix;
		this.varname = var.getName();
		this.this_varname = "this." + this.varname;
		this.Varname = StringUtils.upper1(varname);
	}

	private void zdb_verify(String methodName) {
		Zdbgen.verify(ps, prefix + "	", "this", methodName, false);
	}

	@Override
	public void visit(Cbean type) {
		String typename = TypeName.getName(type);
		simple(typename);
	}

	@Override
	public void visit(Xbean type) {
		// unsupported
	}

	@Override
	public void visit(TypeString type) {
		simple("String", true);
	}

	@Override
	public void visit(TypeBinary type) {
		ps.println(prefix + "public void set" + Varname + "(limax.codec.Marshal _v_) { " + var.getComment());
		zdb_verify("set" + Varname);
		ps.println(prefix + "	limax.zdb.Logs.logObject(this, " + StringUtils.quote(varname) + ");");
		ps.println(prefix + "	" + this_varname + " = _v_.marshal(new OctetsStream()).getBytes();");
		ps.println(prefix + "}");
		ps.println("");

		ps.println(prefix + "public void set" + Varname + "Copy(byte[] _v_) { " + var.getComment());
		zdb_verify("set" + Varname + "Copy");
		ps.println(prefix + "	limax.zdb.Logs.logObject(this, " + StringUtils.quote(varname) + ");");
		ps.println(prefix + "	" + this_varname + " = java.util.Arrays.copyOf(_v_, _v_.length);");
		ps.println(prefix + "}");
		ps.println("");

		ps.println(prefix + "public void set" + Varname + "OctetsCopy(limax.codec.Octets _v_) { " + var.getComment());
		zdb_verify("set" + Varname + "OctetsCopy");
		ps.println(prefix + "	limax.zdb.Logs.logObject(this, " + StringUtils.quote(varname) + ");");
		ps.println(prefix + "	" + this_varname + " = _v_.getBytes();");
		ps.println(prefix + "}");
		ps.println("");
	}

	private void simple(String typename) {
		simple(typename, false);
	}

	private void simple(String typename, boolean checknull) {
		ps.println(prefix + "public void set" + Varname + "(" + typename + " _v_) { " + var.getComment());
		zdb_verify("set" + Varname);
		if (checknull)
			ps.println(prefix + "	java.util.Objects.requireNonNull(_v_);");
		ps.println(prefix + "	limax.zdb.Logs.logObject(this, " + StringUtils.quote(varname) + ");");
		ps.println(prefix + "	" + this_varname + " = _v_;");
		ps.println(prefix + "}");
		ps.println("");
	}

	@Override
	public void visit(TypeBoolean type) {
		simple("boolean");
	}

	@Override
	public void visit(TypeByte type) {
		simple("byte");
	}

	@Override
	public void visit(TypeShort type) {
		simple("short");
	}

	@Override
	public void visit(TypeInt type) {
		simple("int");
	}

	@Override
	public void visit(TypeLong type) {
		simple("long");
	}

	@Override
	public void visit(TypeFloat type) {
		simple("float");
	}

	@Override
	public void visit(TypeDouble type) {
		simple("double");
	}

	@Override
	public void visit(TypeList type) {
		// unsupported
	}

	@Override
	public void visit(TypeVector type) {
		// unsupported
	}

	@Override
	public void visit(TypeSet type) {
		// unsupported
	}

	@Override
	public void visit(TypeMap type) {
		// unsupported
	}

	@Override
	public void visit(TypeAny type) {
		simple(TypeName.getName(type));
	}

	@Override
	public void visit(Bean type) {
		throw new UnsupportedOperationException();
	}
}
