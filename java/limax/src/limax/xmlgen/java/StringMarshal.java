package limax.xmlgen.java;

import java.io.PrintStream;
import java.util.Collection;

import limax.xmlgen.Bean;
import limax.xmlgen.Cbean;
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

class StringMarshal implements Visitor {
	private final PrintStream ps;
	private final String fullvarname;
	private final String varname;
	private final Integer index;
	private final String prefix;

	public static void make(Collection<Variable> variables, PrintStream ps, String prefix) {
		ps.println(prefix + "@Override");
		ps.println(prefix + "public limax.codec.StringStream marshal(limax.codec.StringStream _os_) {");
		ps.println(prefix + "	_os_.append(\"L\");");
		variables.forEach(var -> var.getType()
				.accept(new StringMarshal("this.", var.getName(), (Integer) var.attachment(), ps, prefix + "	")));
		ps.println(prefix + "	return _os_.append(\":\");");
		ps.println(prefix + "}");
		ps.println();
	}

	public static void make(Xbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Cbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Bean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Variable var, PrintStream ps, String prefix) {
		var.getType().accept(new StringMarshal("this.", var.getName(), (Integer) var.attachment(), ps, prefix));
	}

	public static void make(Type type, String varname, PrintStream ps, String prefix) {
		type.accept(new StringMarshal("", varname, null, ps, prefix));
	}

	private StringMarshal(String domain, String varname, Integer index, PrintStream ps, String prefix) {
		this.varname = varname;
		this.fullvarname = domain + varname;
		this.index = index;
		this.ps = ps;
		this.prefix = prefix;
	}

	private void prefix(String type) {
		if (index != null)
			ps.println(prefix + "_os_.variable(\"" + varname + "\")"
					+ (type.isEmpty() ? ";" : ".append(\"" + type + "\");"));
		else if (!type.isEmpty())
			ps.println(prefix + "_os_.append(\"" + type + "\");");
	}

	private void postfix(String type) {
		ps.println(prefix + "_os_.append(\"" + type + "\");");
	}

	private void model0() {
		prefix("");
		ps.println(prefix + "_os_.marshal(" + fullvarname + ");");
	}

	private void model1() {
		prefix("L");
		ps.println(prefix + fullvarname + ".forEach(_v_ -> _os_.marshal(_v_));");
		postfix(":");
	}

	private void model2() {
		prefix("M");
		ps.println(prefix + fullvarname + ".forEach((_k_, _v_) -> _os_.marshal(_k_).marshal(_v_));");
		postfix(":");
	}

	@Override
	public void visit(Bean bean) {
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

	@Override
	public void visit(TypeMap type) {
		model2();
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
