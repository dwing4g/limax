package limax.xmlgen.java;

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
		ps.println(prefix + "@Override");
		ps.println(prefix + "public int compareTo(" + name + " _o_) {");
		ps.println(prefix + "	if (_o_ == this) return 0;");
		ps.println(prefix + "	int _c_ = 0;");
		for (Variable var : variables) {
			CompareTo e = new CompareTo(var.getName(), "_o_");
			var.getType().accept(e);
			ps.println(prefix + "	_c_ = " + e.getText() + ";");
			ps.println(prefix + "	if (0 != _c_) return _c_;");
		}
		ps.println(prefix + "	return _c_;");
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

	private String getText() {
		return text;
	}

	private CompareTo(String variable, String another) {
		this.thisVariable = "this." + variable;
		this.variable = variable;
		this.another = another;
	}

	private void model0() {
		text = thisVariable + " - " + another + "." + variable;
	}

	@Override
	public void visit(Bean type) {
		text = thisVariable + ".compareTo(" + another + "." + variable + ")";
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
		text = "Long.signum(" + thisVariable + " - " + another + "." + variable + ")";
	}

	@Override
	public void visit(TypeFloat type) {
		text = "Float.compare(" + thisVariable + ", " + another + "." + variable + ")";
	}

	@Override
	public void visit(TypeDouble type) {
		text = "Double.compare(" + thisVariable + ", " + another + "." + variable + ")";
	}

	@Override
	public void visit(TypeString type) {
		text = thisVariable + ".compareTo( " + another + "." + variable + ")";
	}

	@Override
	public void visit(TypeBinary type) {
		throw new UnsupportedOperationException("compareTo with " + type);
	}

	@Override
	public void visit(TypeList type) {
		throw new UnsupportedOperationException("compareTo with " + type);
	}

	@Override
	public void visit(TypeVector type) {
		throw new UnsupportedOperationException("compareTo with " + type);
	}

	@Override
	public void visit(TypeSet type) {
		throw new UnsupportedOperationException("compareTo with " + type);
	}

	@Override
	public void visit(TypeMap type) {
		throw new UnsupportedOperationException("compareTo with " + type);
	}

	@Override
	public void visit(Cbean type) {
		text = thisVariable + ".compareTo(" + another + "." + variable + ")";
	}

	@Override
	public void visit(Xbean type) {
		text = thisVariable + ".compareTo(" + another + "." + variable + ")";
	}

	@Override
	public void visit(TypeBoolean type) {
		text = "(" + thisVariable + " ? 1 : 0) - " + "(" + another + "." + variable + " ? 1 : 0)";
	}

	@Override
	public void visit(TypeAny type) {
		throw new UnsupportedOperationException("compareTo with " + type);
	}

}
