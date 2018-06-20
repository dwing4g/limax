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
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;

class Equals implements Visitor {
	private String variable;
	private String another;
	private String text;

	private static void make(Collection<Variable> variables, String name, PrintStream ps, String prefix) {
		ps.println(prefix + "bool equals(const " + name + "& _dst_) const");
		ps.println(prefix + "{");
		ps.println(prefix + "	if(&_dst_ == this)");
		ps.println(prefix + "		return true;");
		for (Variable var : variables) {
			Equals e = new Equals(var.getName(), "_dst_");
			var.getType().accept(e);
			ps.println(prefix + "	if ( !" + e.getText() + ")");
			ps.println(prefix + "		return false;");
		}
		ps.println(prefix + "	return true;");
		ps.println(prefix + "}");
		ps.println();
		ps.println(prefix + "bool operator==(const " + name + "& _dst_) const");
		ps.println(prefix + "{");
		ps.println(prefix + "	return equals(_dst_);");
		ps.println(prefix + "}");
		ps.println();
	}

	public static void make(Bind bind, PrintStream ps, String prefix) {
		make(bind.getVariables(), bind.getName(), ps, prefix);
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

	Equals(String variable, String another) {
		this.variable = variable;
		this.another = another;
	}

	private void model0() {
		text = "(this->" + variable + " == " + another + "." + variable + ")";
	}

	private void model1() {
		text = "this->" + variable + ".equals(" + another + "." + variable + ")";
	}

	private void model2() {
		text = "limax::equals(this->" + variable + ", " + another + "." + variable + ")";
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
		model2();
	}

	@Override
	public void visit(TypeLong type) {
		model0();
	}

	@Override
	public void visit(TypeMap type) {
		model2();
	}

	@Override
	public void visit(TypeBinary type) {
		model0();
	}

	@Override
	public void visit(TypeSet type) {
		model2();
	}

	@Override
	public void visit(TypeString type) {
		model2();
	}

	@Override
	public void visit(TypeVector type) {
		model2();
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
		throw new UnsupportedOperationException("Equals with " + type);
	}
}
