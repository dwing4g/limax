package limax.xmlgen.csharp;

import java.io.PrintStream;
import java.util.Collection;

import limax.xmlgen.Bean;
import limax.xmlgen.Cbean;
import limax.xmlgen.Control;
import limax.xmlgen.Protocol;
import limax.xmlgen.Variable;
import limax.xmlgen.Xbean;

class ConstructWithParam {

	static String getParamList(Collection<Variable> variables) {
		StringBuilder params = new StringBuilder();
		for (Variable var : variables)
			params.append(", ").append(TypeName.getName(var.getType())).append(" _").append(var.getName()).append("_");
		return params.delete(0, 2).toString();
	}

	private static void make(Collection<Variable> vars, String classname, PrintStream ps, String prefix) {
		if (vars.isEmpty())
			return;
		ps.println(prefix + "public " + classname + "(" + getParamList(vars) + ")");
		ps.println(prefix + "{");
		vars.forEach(var -> ps.println(prefix + "	this." + var.getName() + " = _" + var.getName() + "_;"));
		ps.println(prefix + "}");
	}

	private static void makeControl(Collection<Variable> vars, String classname, PrintStream ps, String prefix) {
		if (vars.isEmpty()) {
			ps.println(prefix + "public " + classname + "(View _view_) : base(_view_)");
			ps.println(prefix + "{");
			ps.println(prefix + "}");
		} else {
			ps.println(prefix + "public " + classname + "(View _view_, " + getParamList(vars) + ") : base(_view_)");
			ps.println(prefix + "{");
			vars.forEach(var -> ps.println(prefix + "	this." + var.getName() + " = _" + var.getName() + "_;"));
			ps.println(prefix + "}");
		}
	}

	public static void make(Protocol protocol, PrintStream ps, String prefix) {
		make(protocol.getImplementBean().getVariables(), protocol.getLastName(), ps, prefix);
	}

	public static void make(Control control, PrintStream ps, String prefix) {
		makeControl(control.getImplementBean().getVariables(), "__" + control.getName(), ps, prefix);
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

}
