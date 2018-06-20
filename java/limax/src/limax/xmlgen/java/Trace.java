package limax.xmlgen.java;

import java.io.PrintStream;
import java.util.Collection;

import limax.xmlgen.Bean;
import limax.xmlgen.Cbean;
import limax.xmlgen.Main;
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

class Trace implements Visitor {
	private final String varname;
	private final PrintStream ps;
	private final String prefix;

	public static void make(Bean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Xbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Cbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Collection<Variable> variables, PrintStream ps, String prefix) {
		ps.println(prefix + "@Override");
		ps.println(prefix + "public String toString() {");
		ps.println(prefix + "	StringBuilder _sb_ = new StringBuilder(super.toString());");
		ps.println(prefix + "	_sb_.append(\"=(\");");
		variables.forEach(var -> var.getType().accept(new Trace(var.getName(), ps, prefix + "	")));
		ps.println(prefix + "	_sb_.append(\")\");");
		ps.println(prefix + "	return _sb_.toString();");
		ps.println(prefix + "}");
		ps.println();
	}

	private Trace(String varname, PrintStream ps, String prefix) {
		this.varname = "this." + varname;
		this.ps = ps;
		this.prefix = prefix;
	}

	private void simple() {
		ps.println(prefix + "_sb_.append(" + varname + ").append(\",\");");
	}

	@Override
	public void visit(Bean bean) {
		simple();
	}

	@Override
	public void visit(TypeByte type) {
		simple();
	}

	@Override
	public void visit(TypeFloat type) {
		simple();
	}

	@Override
	public void visit(TypeDouble type) {
		simple();
	}

	@Override
	public void visit(TypeInt type) {
		simple();
	}

	@Override
	public void visit(TypeShort type) {
		simple();
	}

	@Override
	public void visit(TypeList type) {
		simple();
	}

	@Override
	public void visit(TypeLong type) {
		simple();
	}

	@Override
	public void visit(TypeMap type) {
		simple();
	}

	@Override
	public void visit(TypeBinary type) {
		ps.println(prefix + "_sb_.append(\"B\").append(" + varname + "." + (Main.isMakingZdb ? "length" : "size()")
				+ ").append(\",\");");
	}

	@Override
	public void visit(TypeSet type) {
		simple();
	}

	@Override
	public void visit(TypeString type) {
		ps.println(prefix + "_sb_.append(\"T\").append(" + varname + ".length()).append(\",\");");
	}

	@Override
	public void visit(TypeVector type) {
		simple();
	}

	@Override
	public void visit(Cbean type) {
		simple();
	}

	@Override
	public void visit(Xbean type) {
		simple();
	}

	@Override
	public void visit(TypeBoolean type) {
		simple();
	}

	@Override
	public void visit(TypeAny type) {
		simple();
	}

}
