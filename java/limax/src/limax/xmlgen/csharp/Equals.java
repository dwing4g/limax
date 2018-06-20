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

class Equals implements Visitor {
	private String variable;
	private String another;
	private String text;

	private static void make(Collection<Variable> variables, String name, PrintStream ps, String prefix) {
		ps.println(prefix + "public override bool Equals(object __o1__)");
		ps.println(prefix + "{");
		ps.println(prefix + "	if(__o1__ == this)");
		ps.println(prefix + "		return true;");
		ps.println(prefix + "	if(__o1__.GetType() != this.GetType())");
		ps.println(prefix + "		return false;");
		if (!variables.isEmpty())
			ps.println(prefix + "	" + name + " __o__ = (" + name + ")__o1__;");
		for (Variable var : variables) {
			Equals e = new Equals("this." + var.getName(), "__o__." + var.getName());
			var.getType().accept(e);
			ps.println(prefix + "	if (!" + e.getText() + ")");
			ps.println(prefix + "		return false;");
		}
		ps.println(prefix + "	return true;");
		ps.println(prefix + "}");
	}

	public static void make(Bind bind, PrintStream ps, String prefix) {
		make(bind.getVariables(), "__" + bind.getName(), ps, prefix);
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

	public String getText() {
		return text;
	}

	private Equals(String variable, String another) {
		this.variable = variable;
		this.another = another;
	}

	private void model0() {
		text = "Utils.equals(" + variable + ", " + another + ")";
	}

	@Override
	public void visit(Bean type) {
		model0();
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
		model0();
	}

	@Override
	public void visit(TypeLong type) {
		model0();
	}

	@Override
	public void visit(TypeMap type) {
		model0();
	}

	@Override
	public void visit(TypeBinary type) {
		model0();
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
		throw new UnsupportedOperationException("Equals with " + type);
	}
}
