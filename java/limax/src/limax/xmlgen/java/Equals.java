package limax.xmlgen.java;

import java.io.PrintStream;
import java.util.Collection;

import limax.xmlgen.Bean;
import limax.xmlgen.Bind;
import limax.xmlgen.Cbean;
import limax.xmlgen.Main;
import limax.xmlgen.Monitorset;
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

class Equals implements Visitor {
	private String variable;
	private String another;
	private String text;

	private static void make(Collection<? extends Variable> variables, String name, PrintStream ps, String prefix) {
		ps.println(prefix + "@Override");
		ps.println(prefix + "public boolean equals(Object _o1_) {");
		ps.println(prefix + "	if (_o1_ == this) return true;");
		ps.println(prefix + "	if (_o1_ instanceof " + name + ") {");
		if (!variables.isEmpty())
			ps.println(prefix + "		" + name + " _o_ = (" + name + ")_o1_;");
		for (Variable var : variables) {
			Equals e = new Equals(var.getName(), "_o_");
			var.getType().accept(e);
			ps.println(prefix + "		if (" + e.getText() + ") return false;");
		}
		ps.println(prefix + "		return true;");
		ps.println(prefix + "	}");
		ps.println(prefix + "	return false;");
		ps.println(prefix + "}");
		ps.println();
	}

	public static void make(Bean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), bean.getLastName(), ps, prefix);
	}

	public static void make(Cbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), bean.getLastName(), ps, prefix);
	}

	public static void make(Xbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), bean.getLastName(), ps, prefix);
	}

	public static void make(Bind bind, PrintStream ps, String prefix) {
		make(bind.getVariables(), bind.getName(), ps, prefix);
	}

	public static void make(Variable var, PrintStream ps, String prefix) {
		Equals e = new Equals(var.getName(), "_o_");
		var.getType().accept(e);
		ps.println(prefix + "if (" + e.getText() + ") return false;");
	}

	public static void make(Monitorset cts, PrintStream ps, String prefix) {
		make(cts.getKeys(), "_Keys_", ps, prefix);
	}

	public String getText() {
		return text;
	}

	private Equals(String variable, String another) {
		this.variable = variable;
		this.another = another;
	}

	private void model0() {
		text = "this." + variable + " != " + another + "." + variable;
	}

	private void model1() {
		text = "!" + "this." + variable + ".equals(" + another + "." + variable + ")";
	}

	@Override
	public void visit(Bean type) {
		model1();
	}

	@Override
	public void visit(TypeByte type) {
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
	public void visit(TypeInt type) {
		model0();
	}

	@Override
	public void visit(TypeShort type) {
		model0();
	}

	@Override
	public void visit(TypeList type) {
		model1();
	}

	@Override
	public void visit(TypeLong type) {
		model0();
	}

	@Override
	public void visit(TypeMap type) {
		model1();
	}

	@Override
	public void visit(TypeBinary type) {
		if (Main.isMakingZdb)
			text = "!java.util.Arrays.equals(" + "this." + variable + ", " + another + "." + variable + ")";
		else
			model1();
	}

	@Override
	public void visit(TypeSet type) {
		model1();
	}

	@Override
	public void visit(TypeString type) {
		model1();
	}

	@Override
	public void visit(TypeVector type) {
		model1();
	}

	@Override
	public void visit(Cbean type) {
		model1();
	}

	@Override
	public void visit(Xbean type) {
		model1();
	}

	@Override
	public void visit(TypeBoolean type) {
		model0();
	}

	@Override
	public void visit(TypeAny type) {
		text = String.format(
				"(null == this.%1$s && null != %2$s.%1$s) || (null != this.%1$s && null == %2$s.%1$s) || (null != this.%1$s && null != %2$s.%1$s && !this.%1$s.equals(%2$s.%1$s))",
				variable, another);
	}
}
