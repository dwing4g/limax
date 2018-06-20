package limax.xmlgen.java;

import java.io.PrintStream;
import java.util.Collection;

import limax.xmlgen.Bean;
import limax.xmlgen.Variable;

class ConstructWithParam {

	static String getParamList(Collection<Variable> variables) {
		StringBuilder params = new StringBuilder();
		variables.forEach(var -> params.append(", ").append(TypeName.getName(var.getType())).append(" _")
				.append(var.getName()).append("_"));
		return params.delete(0, 2).toString();
	}

	static void make(Collection<Variable> vars, String classname, PrintStream ps, String prefix) {
		if (vars.isEmpty())
			return;
		ps.println(prefix + "public " + classname + "(" + getParamList(vars) + ") {");
		vars.forEach(var -> ps.println(prefix + "	this." + var.getName() + " = _" + var.getName() + "_;"));
		ps.println(prefix + "}");
		ps.println();
	}

	static void make(Bean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), bean.getLastName(), ps, prefix);
	}

}
