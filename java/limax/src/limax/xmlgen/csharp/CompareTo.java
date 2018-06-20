package limax.xmlgen.csharp;

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

class CompareTo implements Visitor {
	private String thisVariable;
	private String variable;
	private String another;
	private String text;

	private static void make(Collection<Variable> variables, String name, PrintStream ps, String prefix) {
		ps.println(prefix + "public int CompareTo(" + name + " __o__)");
		ps.println(prefix + "{");
		ps.println(prefix + "	if (__o__ == this)");
		ps.println(prefix + "		 return 0;");
		ps.println(prefix + "	int __c__ = 0;");
		for (Variable var : variables) {
			CompareTo e = new CompareTo(var.getName(), "__o__");
			var.getType().accept(e);
			ps.println(prefix + "	__c__ = " + e.getText() + ";");
			ps.println(prefix + "	if (0 != __c__)");
			ps.println(prefix + "		return __c__;");
		}
		ps.println(prefix + "	return __c__;");
		ps.println(prefix + "}");
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

	CompareTo(String variable, String another) {
		this.thisVariable = "this." + variable;
		this.variable = variable;
		this.another = another;
	}

	private void model0() {
		text = thisVariable + ".CompareTo( " + another + "." + variable + ")";
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
	public void visit(TypeFloat type) {
		model0();
	}

	@Override
	public void visit(TypeDouble type) {
		model0();
	}

	@Override
	public void visit(TypeString type) {
		model0();
	}

	@Override
	public void visit(TypeBinary type) {
		model0();
	}

	@Override
	public void visit(TypeList type) {
		throw new UnsupportedOperationException("CompareTo with " + type);
	}

	@Override
	public void visit(TypeVector type) {
		throw new UnsupportedOperationException("CompareTo with " + type);
	}

	@Override
	public void visit(TypeSet type) {
		throw new UnsupportedOperationException("CompareTo with " + type);
	}

	@Override
	public void visit(TypeMap type) {
		throw new UnsupportedOperationException("CompareTo with " + type);
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
	}

}
