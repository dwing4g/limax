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

class Unmarshal implements Visitor {
	private String varname;
	private PrintStream ps;
	private String prefix;

	private static void make(Collection<Variable> vars, PrintStream ps, String prefix) {
		ps.println(prefix + "const limax::UnmarshalStream& unmarshal(const limax::UnmarshalStream& _os_) override");
		ps.println(prefix + "{");
		for (Variable var : vars)
			var.getType().accept(new Unmarshal(var.getName(), ps, prefix + "	"));
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

	public static void make(View view, PrintStream ps, String prefix) {
		ps.println(prefix + "const limax::UnmarshalStream& unmarshal(const limax::UnmarshalStream& _os_) override");
		ps.println(prefix + "{");
		for (Variable var : view.getVariables())
			var.getType().accept(new Unmarshal(var.getName(), ps, prefix + "	"));
		for (Bind bind : view.getBinds())
			ps.println(prefix + "	_os_ >> " + bind.getName() + ";");
		ps.println(prefix + "	return _os_;");
		ps.println(prefix + "}");
		ps.println();

	}

	public static void make(Bind bind, PrintStream ps, String prefix) {
		make(bind.getVariables(), ps, prefix);
	}

	public Unmarshal(String varname, PrintStream ps, String prefix) {
		this.varname = varname;
		this.ps = ps;
		this.prefix = prefix;
	}

	@Override
	public void visit(Bean bean) {
		ps.println(prefix + "_os_ >> " + varname + ";");
	}

	@Override
	public void visit(TypeByte type) {
		ps.println(prefix + "_os_ >> " + varname + ";");
	}

	@Override
	public void visit(TypeFloat type) {
		ps.println(prefix + "_os_ >> " + varname + ";");
	}

	@Override
	public void visit(TypeDouble type) {
		ps.println(prefix + "_os_ >> " + varname + ";");
	}

	@Override
	public void visit(TypeInt type) {
		ps.println(prefix + "_os_ >> " + varname + ";");
	}

	@Override
	public void visit(TypeShort type) {
		ps.println(prefix + "_os_ >> " + varname + ";");
	}

	@Override
	public void visit(TypeLong type) {
		ps.println(prefix + "_os_ >> " + varname + ";");
	}

	@Override
	public void visit(TypeBinary type) {
		ps.println(prefix + "_os_ >> " + varname + ";");
	}

	@Override
	public void visit(TypeString type) {
		ps.println(prefix + "_os_ >> " + varname + ";");
	}

	@Override
	public void visit(TypeList type) {
		ps.println(prefix + "_os_ >> limax::MarshalContainer(" + varname + ");");
	}

	@Override
	public void visit(TypeVector type) {
		ps.println(prefix + "_os_ >> limax::MarshalContainer(" + varname + ");");
	}

	@Override
	public void visit(TypeSet type) {
		ps.println(prefix + "_os_ >> limax::MarshalContainer(" + varname + ");");
	}

	@Override
	public void visit(TypeMap type) {
		ps.println(prefix + "_os_ >> limax::MarshalContainer(" + varname + ");");
	}

	@Override
	public void visit(Cbean type) {
		ps.println(prefix + "_os_ >> " + varname + ";");
	}

	@Override
	public void visit(Xbean type) {
		ps.println(prefix + "_os_ >> " + varname + ";");
	}

	@Override
	public void visit(TypeBoolean type) {
		ps.println(prefix + "_os_ >> " + varname + ";");
	}

	@Override
	public void visit(TypeAny type) {
		throw new UnsupportedOperationException();
	}
}
