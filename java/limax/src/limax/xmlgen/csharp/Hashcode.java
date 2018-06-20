package limax.xmlgen.csharp;

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
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;

class Hashcode implements Visitor {

	private static void make(Collection<Variable> variables, PrintStream ps, String prefix) {
		ps.println(prefix + "public override int GetHashCode()");
		ps.println(prefix + "{");
		ps.println(prefix + "	int __h__ = 0;");
		for (Variable var : variables) {
			Hashcode e = new Hashcode(var.getName());
			var.getType().accept(e);
			ps.println(prefix + "	__h__ += __h__ * 31 + " + e.getText() + ";");
		}
		ps.println(prefix + "	return __h__;");
		ps.println(prefix + "}");
	}

	public static void make(Cbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Xbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Bean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Bind bind, PrintStream ps, String prefix) {
		make(bind.getVariables(), ps, prefix);
	}

	private String varname;
	private String text;

	public String getText() {
		return text;
	}

	private Hashcode(String varname) {
		this.varname = "this." + varname;
	}

	private void model0() {
		text = varname + ".GetHashCode()";
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
	public void visit(TypeLong type) {
		model0();
	}

	@Override
	public void visit(TypeBinary type) {
		model0();
	}

	@Override
	public void visit(TypeString type) {
		model0();
	}

	private void model1() {
		text = "Utils.hashCode(" + varname + ")";
	}

	@Override
	public void visit(TypeList type) {
		model1();
	}

	@Override
	public void visit(TypeVector type) {
		model1();
	}

	@Override
	public void visit(TypeSet type) {
		model1();
	}

	@Override
	public void visit(TypeMap type) {
		model1();
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
	public void visit(Bean type) {
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
