package limax.xmlgen.java;

import java.io.PrintStream;

import limax.util.StringUtils;
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
import limax.xmlgen.Variable;
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;

class VarGetter implements Visitor {

	static void make(Variable var, PrintStream ps, String prefix) {
		var.getType().accept(new VarGetter(var, ps, prefix));
	}

	private Variable var;
	private PrintStream ps;
	private String prefix;
	private String varname;
	private String Varname;
	private String this_varname;

	private VarGetter(Variable var, PrintStream ps, String prefix) {
		this.var = var;
		this.ps = ps;
		this.prefix = prefix;
		this.varname = var.getName();
		this.this_varname = "this." + this.varname;
		this.Varname = StringUtils.upper1(this.varname);
	}

	private void zdb_verify(String methodName) {
		Zdbgen.verify(ps, prefix + "	", "this", methodName, true);
	}

	@Override
	public void visit(TypeBinary type) {
		ps.println(prefix + "public <T extends limax.codec.Marshal> T get" + Varname + "(T _v_) { " + var.getComment());
		zdb_verify("get" + Varname);
		ps.println(prefix + "	try {");
		ps.println(prefix + "		_v_.unmarshal(OctetsStream.wrap(limax.codec.Octets.wrap(" + this_varname + ")));");
		ps.println(prefix + "		return _v_;");
		ps.println(prefix + "	} catch (MarshalException _e_) {");
		ps.println(prefix + "		throw new limax.zdb.XError(_e_);");
		ps.println(prefix + "	}");
		ps.println(prefix + "}");
		ps.println("");

		ps.println(prefix + "public boolean is" + Varname + "Empty() { " + var.getComment());
		zdb_verify("is" + Varname + "Empty");
		ps.println(prefix + "	return " + this_varname + ".length == 0;");
		ps.println(prefix + "}");
		ps.println("");

		ps.println(prefix + "public byte[] get" + Varname + "Copy() { " + var.getComment());
		zdb_verify("get" + Varname + "Copy");
		ps.println(prefix + "	return java.util.Arrays.copyOf(" + this_varname + ", " + this_varname + ".length);");
		ps.println(prefix + "}");
		ps.println("");

		ps.println(prefix + "public limax.codec.Octets get" + Varname + "OctetsCopy() { " + var.getComment());
		ps.println(prefix + "	return new limax.codec.Octets(" + this_varname + ");");
		ps.println(prefix + "}");
		ps.println("");
	}

	private void simple(Type type) {
		simple(type, this_varname);
	}

	private void simple(Type type, String ret) {
		String gettername = TypeName.getGetterName(type);
		ps.println(prefix + "public " + gettername + " get" + Varname + "() { " + var.getComment());
		zdb_verify("get" + Varname);
		ps.println(prefix + "	return " + ret + ";");
		ps.println(prefix + "}");
		ps.println("");
	}

	private void container(Type type, String posttype) {
		String gettername = TypeName.getGetterName(type);
		ps.println(prefix + "public " + gettername + " get" + Varname + "() {  " + var.getComment());
		ps.println(prefix + "	return limax.zdb.Transaction.isActive() ? limax.zdb.Logs.log" + posttype + "(this, "
				+ StringUtils.quote(varname) + ", " + Zdbgen.verifyString("this", "get" + Varname, true) + ") : "
				+ this_varname + ";");
		ps.println(prefix + "}");
		ps.println("");
	}

	@Override
	public void visit(Cbean type) {
		simple(type);
	}

	@Override
	public void visit(Xbean type) {
		simple(type);
	}

	@Override
	public void visit(TypeString type) {
		simple(type);
	}

	@Override
	public void visit(TypeBoolean type) {
		simple(type);
	}

	@Override
	public void visit(TypeByte type) {
		simple(type);
	}

	@Override
	public void visit(TypeShort type) {
		simple(type);
	}

	@Override
	public void visit(TypeInt type) {
		simple(type);
	}

	@Override
	public void visit(TypeLong type) {
		simple(type);
	}

	@Override
	public void visit(TypeFloat type) {
		simple(type);
	}

	@Override
	public void visit(TypeDouble type) {
		simple(type);
	}

	@Override
	public void visit(TypeList type) {
		container(type, "List");
	}

	@Override
	public void visit(TypeVector type) {
		container(type, "List");
	}

	@Override
	public void visit(TypeSet type) {
		container(type, "Set");
	}

	@Override
	public void visit(TypeMap type) {
		container(type, "Map");
	}

	@Override
	public void visit(TypeAny type) {
		simple(type);
	}

	@Override
	public void visit(Bean type) {
		throw new UnsupportedOperationException();
	}

}
