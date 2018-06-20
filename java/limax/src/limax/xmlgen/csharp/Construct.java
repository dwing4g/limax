package limax.xmlgen.csharp;

import java.io.PrintStream;
import java.util.Collection;

import limax.xmlgen.Bean;
import limax.xmlgen.Bind;
import limax.xmlgen.Cbean;
import limax.xmlgen.Protocol;
import limax.xmlgen.Type;
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

class Construct implements Visitor {
	private final PrintStream ps;
	private final String variable;
	private final String prefix;

	private static void make(String name, Collection<Variable> vars, PrintStream ps, String prefix) {
		ps.println(prefix + "public " + name + "()");
		ps.println(prefix + "{");
		for (Variable var : vars)
			var.getType().accept(new Construct(var, ps, prefix + "	"));
		ps.println(prefix + "}");
	}

	public static void make(Protocol protocol, PrintStream ps, String prefix) {
		make(protocol.getLastName(), protocol.getImplementBean().getVariables(), ps, prefix);
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

	public static void make(Bind bind, PrintStream ps, String prefix) {
		make("__" + bind.getName(), bind.getVariables(), ps, prefix);
	}

	private Construct(Variable variable, PrintStream ps, String prefix) {
		this.ps = ps;
		this.variable = variable.getName();
		this.prefix = prefix;
	}

	private Construct(String variable, PrintStream ps, String prefix) {
		this.ps = ps;
		this.variable = variable;
		this.prefix = prefix;
	}

	private void initialInteger() {
		ps.println(prefix + variable + " = 0;");
	}

	@Override
	public void visit(Bean type) {
		ps.println(prefix + variable + " = new " + type.getFullName() + "();");
	}

	@Override
	public void visit(TypeByte type) {
		this.initialInteger();
	}

	@Override
	public void visit(TypeInt type) {
		this.initialInteger();
	}

	@Override
	public void visit(TypeShort type) {
		this.initialInteger();
	}

	@Override
	public void visit(TypeLong type) {
		this.initialInteger();
	}

	@Override
	public void visit(TypeBinary type) {
		ps.println(prefix + variable + " = new limax.codec.Octets();");
	}

	@Override
	public void visit(TypeString type) {
		ps.println(prefix + variable + " = \"\";");
	}

	private void outputContainer(Type type) {
		ps.println(prefix + variable + " = new " + TypeName.getName(type) + "();");
	}

	@Override
	public void visit(TypeList type) {
		outputContainer(type);
	}

	@Override
	public void visit(TypeVector type) {
		outputContainer(type);
	}

	@Override
	public void visit(TypeSet type) {
		outputContainer(type);
	}

	@Override
	public void visit(TypeMap type) {
		outputContainer(type);
	}

	@Override
	public void visit(TypeFloat type) {
		ps.println(prefix + variable + " = 0.0f;");
	}

	@Override
	public void visit(TypeDouble type) {
		ps.println(prefix + variable + " = 0.0;");
	}

	@Override
	public void visit(Cbean type) {
		ps.print(prefix + variable + " = new " + type.getFullName() + "();");
	}

	@Override
	public void visit(Xbean type) {
		ps.print(prefix + variable + " = new " + type.getFullName() + "();");
	}

	@Override
	public void visit(TypeBoolean type) {
		ps.println(prefix + variable + " = false;");
	}

	@Override
	public void visit(TypeAny type) {
		throw new UnsupportedOperationException();
	}

}
