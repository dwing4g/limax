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

class JSONMarshal implements Visitor {
	private final PrintStream ps;
	private final String fullvarname;
	private final String varname;
	private final String prefix;

	public static void make(Collection<Variable> variables, PrintStream ps, String prefix) {
		ps.println(prefix + "public JSONBuilder marshal(JSONBuilder _os_)");
		ps.println(prefix + "{");
		ps.println(prefix + "	_os_.begin();");
		variables.stream().filter(var -> var.isJSONEnabled())
				.forEach(var -> var.getType().accept(new JSONMarshal("this.", var.getName(), ps, prefix + "	")));
		ps.println(prefix + "	return _os_.end();");
		ps.println(prefix + "}");
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
		var.getType().accept(new JSONMarshal("this.", var.getName(), ps, prefix));
	}

	private JSONMarshal(String domain, String varname, PrintStream ps, String prefix) {
		this.varname = varname;
		this.fullvarname = domain + varname;
		this.ps = ps;
		this.prefix = prefix;
	}

	private void model0() {
		ps.println(prefix + "_os_.Append(\"" + varname + "\").colon().Append(" + fullvarname + ").comma();");
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
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(TypeString type) {
		model0();
	}

	@Override
	public void visit(TypeMap type) {
		model0();
	}

	@Override
	public void visit(TypeList type) {
		model0();
	}

	@Override
	public void visit(TypeSet type) {
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
		throw new UnsupportedOperationException();
	}

}
