package limax.xmlgen.cpp;

import java.io.PrintStream;
import java.util.Collection;

import limax.xmlgen.Bean;
import limax.xmlgen.Bind;
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
import limax.xmlgen.View;
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;

class Marshal implements Visitor {
	private final PrintStream ps;
	private final String prefix;
	private final String varname;

	private static void make(Collection<Variable> vars, PrintStream ps, String prefix) {
		ps.println(prefix + "limax::MarshalStream& marshal(limax::MarshalStream& _os_) const override");
		ps.println(prefix + "{");
		vars.forEach(var -> var.getType().accept(new Marshal(ps, prefix + "	", var)));
		ps.println(prefix + "	return _os_;");
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

	public static void make(Bind bind, PrintStream ps, String prefix) {
		make(bind.getVariables(), ps, prefix);
	}

	public static void printMarshalViewElement(View view, PrintStream ps, String prefix) {
		view.getVariables().forEach(var -> var.getType().accept(new Marshal(ps, prefix, var)));
		view.getBinds().forEach(bind -> ps.println(prefix + "_os_ << " + bind.getName() + ";"));
	}

	public Marshal(PrintStream ps, String prefix, Variable var) {
		this.ps = ps;
		this.prefix = prefix;
		this.varname = var.getName();
	}

	@Override
	public void visit(TypeByte type) {
		ps.println(prefix + "_os_ << " + varname + ";");
	}

	@Override
	public void visit(TypeInt type) {
		ps.println(prefix + "_os_ << " + varname + ";");
	}

	@Override
	public void visit(TypeShort type) {
		ps.println(prefix + "_os_ << " + varname + ";");
	}

	@Override
	public void visit(TypeFloat type) {
		ps.println(prefix + "_os_ << " + varname + ";");
	}

	@Override
	public void visit(TypeDouble type) {
		ps.println(prefix + "_os_ << " + varname + ";");
	}

	@Override
	public void visit(TypeLong type) {
		ps.println(prefix + "_os_ << " + varname + ";");
	}

	@Override
	public void visit(TypeList type) {
		ps.println(prefix + "_os_ << limax::MarshalContainer(" + varname + ");");
	}

	@Override
	public void visit(TypeMap type) {
		ps.println(prefix + "_os_ << limax::MarshalContainer(" + varname + ");");
	}

	@Override
	public void visit(TypeBinary type) {
		ps.println(prefix + "_os_ << " + varname + ";");
	}

	@Override
	public void visit(TypeSet type) {
		ps.println(prefix + "_os_ << limax::MarshalContainer(" + varname + ");");
	}

	@Override
	public void visit(TypeString type) {
		ps.println(prefix + "_os_ << " + varname + ";");
	}

	@Override
	public void visit(TypeVector type) {
		ps.println(prefix + "_os_ << limax::MarshalContainer(" + varname + ");");
	}

	@Override
	public void visit(Bean bean) {
		ps.println(prefix + "_os_ << " + varname + ";");
	}

	@Override
	public void visit(Cbean type) {
		ps.println(prefix + "_os_ << " + varname + ";");
	}

	@Override
	public void visit(Xbean type) {
		ps.println(prefix + "_os_ << " + varname + ";");
	}

	@Override
	public void visit(TypeBoolean type) {
		ps.println(prefix + "_os_ << " + varname + ";");
	}

	@Override
	public void visit(TypeAny type) {
		throw new UnsupportedOperationException();
	}

}
