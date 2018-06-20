package limax.xmlgen.cpp;

import java.io.PrintStream;
import java.util.Collection;

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

class JSONMarshal implements Visitor {
	private final PrintStream ps;
	private final String prefix;
	private final String varname;

	private static void make(Collection<Variable> vars, PrintStream ps, String prefix) {
		ps.println(prefix + "limax::JSONBuilder& marshal(limax::JSONBuilder& _os_) const override");
		ps.println(prefix + "{");
		ps.println(prefix + "	_os_.begin();");
		vars.stream().filter(var -> var.isJSONEnabled())
				.forEach(var -> var.getType().accept(new JSONMarshal(ps, prefix + "	", var)));
		ps.println(prefix + "	return _os_.end();");
		ps.println(prefix + "}");
		ps.println();
	}

	public static void make(Bean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Cbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Xbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public JSONMarshal(PrintStream ps, String prefix, Variable var) {
		this.ps = ps;
		this.prefix = prefix;
		this.varname = var.getName();
	}

	private void model0() {
		ps.println(prefix + "_os_.append(\"" + varname + "\").colon().append(" + varname + ").comma();");
	}

	@Override
	public void visit(TypeByte type) {
		model0();
	}

	@Override
	public void visit(TypeInt type) {
		model0();
	}

	@Override
	public void visit(TypeShort type) {
		model0();
	}

	@Override
	public void visit(TypeFloat type) {
		model0();
	}

	@Override
	public void visit(TypeDouble type) {
		model0();
	}

	@Override
	public void visit(TypeLong type) {
		model0();
	}

	@Override
	public void visit(TypeList type) {
		model0();
	}

	@Override
	public void visit(TypeMap type) {
		model0();
	}

	@Override
	public void visit(TypeBinary type) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(TypeSet type) {
		model0();
	}

	@Override
	public void visit(TypeString type) {
		model0();
	}

	@Override
	public void visit(TypeVector type) {
		model0();
	}

	@Override
	public void visit(Bean bean) {
		model0();
	}

	@Override
	public void visit(Cbean type) {
		model0();
	}

	@Override
	public void visit(Xbean type) {
		model0();
	}

	@Override
	public void visit(TypeBoolean type) {
		model0();
	}

	@Override
	public void visit(TypeAny type) {
		throw new UnsupportedOperationException();
	}

}
