package limax.xmlgen.java;

import java.io.PrintStream;
import java.util.Collection;

import limax.util.StringUtils;
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

class VarShallowCopy implements Visitor {
	private final PrintStream ps;
	private final String varname;
	private final String thisVar;
	private final String prefix;

	private static void make(Collection<Variable> variables, String className, String paramType, PrintStream ps,
			String prefix) {
		ps.println(prefix + "public " + className + "(" + paramType + " _o_) {");
		for (Variable var : variables)
			var.getType().accept(new VarShallowCopy(var, ps, prefix + "	"));
		ps.println(prefix + "}");
		ps.println();
	}

	public static void make(Bind bind, PrintStream ps, String prefix) {
		make(bind.getVariables(), bind.getName(), TypeName.getName(bind.getValueType()), ps, prefix);
	}

	private VarShallowCopy(Variable var, PrintStream ps, String prefix) {
		this.ps = ps;
		this.prefix = prefix;
		this.varname = var.getName();
		this.thisVar = "this." + this.varname;
	}

	private void model1() {
		ps.println(prefix + thisVar + " = _o_.get" + StringUtils.upper1(varname) + "();");
	}

	@Override
	public void visit(Bean type) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(TypeByte type) {
		model1();
	}

	@Override
	public void visit(TypeShort type) {
		model1();
	}

	@Override
	public void visit(TypeInt type) {
		model1();
	}

	@Override
	public void visit(TypeLong type) {
		model1();
	}

	@Override
	public void visit(TypeFloat type) {
		model1();
	}

	@Override
	public void visit(TypeDouble type) {
		model1();
	}

	@Override
	public void visit(TypeString type) {
		model1();
	}

	@Override
	public void visit(TypeList type) {
		model1();
	}

	@Override
	public void visit(TypeSet type) {
		model1();
	}

	@Override
	public void visit(TypeVector type) {
		model1();
	}

	@Override
	public void visit(TypeMap type) {
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
		model1();
	}

	@Override
	public void visit(TypeAny type) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(TypeBinary type) {
		model1();
	}

}
