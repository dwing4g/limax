package limax.xmlgen.cpp;

import java.io.File;
import java.io.PrintStream;
import java.util.Collection;

import limax.xmlgen.Bean;
import limax.xmlgen.Enum;
import limax.xmlgen.FileOperation;
import limax.xmlgen.Variable;

class BeanFormatter {

	static final String baseClassName = "limax::Marshal";

	private final Bean bean;

	BeanFormatter(Bean bean) {
		this.bean = bean;
	}

	static void printCommonInclude(PrintStream ps) {
		ps.println("#include <limax.h>");
	}

	static String getOutputFileName(String fullname) {
		int index = fullname.indexOf('.');
		return -1 == index ? fullname : fullname.substring(index + 1);
	}

	void make(File output) {
		try (PrintStream ps = FileOperation.fopen(output, getOutputFileName(bean.getFullName()) + ".h")) {
			ps.println();
			ps.println("#pragma once");
			ps.println();
			printCommonInclude(ps);
			for (String inc : Include.includes(bean, ""))
				ps.println(inc);
			ps.println();

			Xmlgen.begin(bean.getFirstName(), ps);
			ps.println();
			ps.println("	class " + bean.getName() + " : public " + baseClassName
					+ (bean.isJSONEnabled() ? ", public limax::JSONMarshal" : ""));
			ps.println("	{");
			ps.println("	public:");
			printDefines(ps);
			ps.println("	};");
			ps.println();
			Xmlgen.end(bean.getFirstName(), ps);
		}
	}

	private void printDefines(PrintStream ps) {
		declareEnums(ps);
		declareVariables(ps);
		Construct.make(bean, ps, "		");
		declareInitConstruct(ps, bean.getName(), bean.getVariables(), "		");
		Marshal.make(bean, ps, "		");
		Unmarshal.make(bean, ps, "		");
		if (bean.isConstType())
			CompareTo.make(bean, ps, "		");
		if (bean.isJSONEnabled())
			JSONMarshal.make(bean, ps, "		");
		Equals.make(bean, ps, "		");
		Hashcode.make(bean, ps, "		");
	}

	static void declareInitConstruct(PrintStream ps, String name, Collection<Variable> vars, String prefix) {
		if (vars.isEmpty())
			return;
		final String beanName = vars.size() == 1 ? ("explicit " + name) : name;
		ps.println(prefix + beanName + "(" + ParamName.getParamList(vars) + ")");
		ps.println(prefix + "	: " + ParamName.getInitConstructList(vars) + " {");
		ps.println(prefix + "}");
	}

	void declareEnums(PrintStream ps) {
		declareEnums(ps, bean.getEnums());
	}

	void declareVariables(PrintStream ps) {
		declareVariables(ps, bean.getVariables(), "		");
	}

	static void declareEnums(PrintStream ps, Collection<Enum> enums) {
		if (enums.isEmpty())
			return;

		ps.println("		enum ");
		ps.println("		{");
		enums.forEach(e -> ps.println("			" + e.getName() + " = " + e.getValue() + "," + e.getComment()));
		ps.println("		};");
		ps.println();
	}

	static void declareVariables(PrintStream ps, Collection<Variable> vars, String prefix) {
		vars.forEach(var -> ps
				.println(prefix + TypeName.getName(var.getType()) + " " + var.getName() + ";" + var.getComment()));
	}

}
