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

class CompareTo implements Visitor {
	private Variable variable;
	private String another;
	private String text;

	private static void make(String name, Collection<Variable> vars, PrintStream ps, String prefix) {
		ps.println(prefix + "bool operator<(const " + name + " &_o_) const");
		ps.println(prefix + "{");
		ps.println(prefix + "	return compareTo(_o_) < 0;");
		ps.println(prefix + "}");
		ps.println();
		ps.println(prefix + "int compareTo(const " + name + " &_o_) const");
		ps.println(prefix + "{");
		ps.println(prefix + "	if (&_o_ == this) return 0;");
		ps.println(prefix + "	int _c_ = 0;");
		for (final Variable var : vars) {
			CompareTo e = new CompareTo(var, "_o_");
			var.getType().accept(e);
			ps.println(prefix + "	_c_ = " + e.getText() + ";");
			ps.println(prefix + "	if (0 != _c_) return _c_;");
		}
		ps.println(prefix + "	return _c_;");
		ps.println(prefix + "}");
		ps.println();
	}

	public static void make(Bean bean, PrintStream ps, String prefix) {
		make(bean.getName(), bean.getVariables(), ps, prefix);
	}

	public static void make(Cbean bean, PrintStream ps, String prefix) {
		make(bean.getName(), bean.getVariables(), ps, prefix);
	}

	public static void make(Xbean bean, PrintStream ps, String prefix) {
		make(bean.getName(), bean.getVariables(), ps, prefix);
	}

	public String getText() {
		return text;
	}

	CompareTo(Variable variable, String another) {
		this.variable = variable;
		this.another = another;
	}

	@Override
	public void visit(Bean type) {
		text = variable.getName() + ".compareTo(" + another + "." + variable.getName() + ")";
	}

	@Override
	public void visit(TypeByte type) {
		text = variable.getName() + " - " + another + "." + variable.getName();
	}

	@Override
	public void visit(TypeInt type) {
		text = variable.getName() + " - " + another + "." + variable.getName();
	}

	@Override
	public void visit(TypeShort type) {
		text = variable.getName() + " - " + another + "." + variable.getName();
	}

	@Override
	public void visit(TypeLong type) {
		text = "limax::signum(" + variable.getName() + " - " + another + "." + variable.getName() + ")";
	}

	@Override
	public void visit(TypeFloat type) {
		// text = "Float.compare(" + variable.getName() + ", " + another + "." +
		// variable.getName() + ")";
		throw new UnsupportedOperationException("compareTo with " + type);
	}

	@Override
	public void visit(TypeDouble type) {
		// text = "Double.compare(" + variable.getName() + ", " + another + "."
		// + variable.getName() + ")";
		throw new UnsupportedOperationException("compareTo with " + type);
	}

	@Override
	public void visit(TypeString type) {
		text = variable.getName() + ".compare(" + another + "." + variable.getName() + ")";
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
		text = variable.getName() + " .compareTo( " + another + "." + variable.getName() + ")";
	}

	@Override
	public void visit(Xbean type) {
		throw new UnsupportedOperationException("compareTo with " + type);
	}

	@Override
	public void visit(TypeBoolean type) {
		text = "limax::signum(" + variable.getName() + " - " + another + "." + variable.getName() + ")";
	}

	@Override
	public void visit(TypeAny type) {
		throw new UnsupportedOperationException("compareTo with " + type);
	}

}
