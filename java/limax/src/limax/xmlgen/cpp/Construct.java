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

class Construct implements Visitor {
	private PrintStream ps;
	private Variable variable;
	private String prefix;

	private static void make(String name, Collection<Variable> vars, PrintStream ps, String prefix) {
		ps.println(prefix + name + "()");
		ps.println(prefix + "{");
		vars.forEach(var -> var.getType().accept(new Construct(ps, var, prefix + "	")));
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

	public static void make(View view, PrintStream ps, String prefix, boolean isTemp) {
		final String parentClassName = isTemp ? "limax::TemporaryView" : "limax::View";
		final String className = isTemp ? ("_" + view.getLastName()) : view.getLastName();
		ps.println(prefix + className + "(std::shared_ptr<limax::ViewContext> vc)");
		ps.println(prefix + "	: " + parentClassName + "(vc)");
		ps.println(prefix + "{");
		ps.println(prefix + "	__getpvid__() = vc->getProviderId();");
		view.getVariables().forEach(var -> var.getType().accept(new Construct(ps, var, prefix + "	")));
		ps.println(prefix + "}");
		ps.println();
	}

	public static void make(Bind bind, PrintStream ps, String prefix) {
		make(bind.getName(), bind.getVariables(), ps, prefix);
	}

	public Construct(PrintStream ps, Variable variable, String prefix) {
		this.ps = ps;
		this.variable = variable;
		this.prefix = prefix;
	}

	@Override
	public void visit(Bean type) {
	}

	@Override
	public void visit(TypeByte type) {
		ps.println(prefix + variable.getName() + " = 0;");
	}

	@Override
	public void visit(TypeInt type) {
		ps.println(prefix + variable.getName() + " = 0;");
	}

	@Override
	public void visit(TypeShort type) {
		ps.println(prefix + variable.getName() + " = 0;");
	}

	@Override
	public void visit(TypeLong type) {
		ps.println(prefix + variable.getName() + " = 0;");
	}

	@Override
	public void visit(TypeBinary type) {
	}

	@Override
	public void visit(TypeString type) {
		ps.println(prefix + variable.getName() + " = \"\";");
	}

	@Override
	public void visit(TypeList type) {
	}

	@Override
	public void visit(TypeVector type) {
	}

	@Override
	public void visit(TypeSet type) {
	}

	@Override
	public void visit(TypeMap type) {
	}

	@Override
	public void visit(TypeFloat type) {
		ps.println(prefix + variable.getName() + " = 0.0f;");
	}

	@Override
	public void visit(TypeDouble type) {
		ps.println(prefix + variable.getName() + " = 0.0;");
	}

	@Override
	public void visit(Cbean type) {
	}

	@Override
	public void visit(Xbean type) {
	}

	@Override
	public void visit(TypeBoolean type) {
		ps.println(prefix + variable.getName() + " = false;");
	}

	@Override
	public void visit(TypeAny type) {
		throw new UnsupportedOperationException();
	}

}
