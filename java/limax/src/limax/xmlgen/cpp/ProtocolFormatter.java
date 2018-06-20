package limax.xmlgen.cpp;

import java.io.File;
import java.io.PrintStream;
import java.util.Collection;

import limax.xmlgen.Bean;
import limax.xmlgen.FileOperation;
import limax.xmlgen.Protocol;
import limax.xmlgen.Service;
import limax.xmlgen.Variable;

class ProtocolFormatter {
	private final Service service;
	private final Protocol protocol;

	public ProtocolFormatter(Service service, Protocol protocol) {
		this.service = service;
		this.protocol = protocol;
	}

	private void printIncludes(PrintStream ps) {
		BeanFormatter.printCommonInclude(ps);
		for (String inc : Include.includes(protocol.getImplementBean(), "../" + Xmlgen.beans_path_name + "/"))
			ps.println(inc);
	}

	private void printDefine(PrintStream ps) {
		final Bean bean = protocol.getImplementBean();
		final BeanFormatter bf = new BeanFormatter(bean);
		// define
		ps.println();
		ps.println("		static int TYPE;");
		ps.println();
		bf.declareEnums(ps);
		bf.declareVariables(ps);

		// default constructor
		ps.println("		" + protocol.getLastName() + "()");
		ps.println("		{");
		ps.println("			type = TYPE;");
		for (Variable var : bean.getVariables())
			var.getType().accept(new Construct(ps, var, "			"));
		ps.println("		}");
		ps.println();

		final Collection<Variable> variables = bean.getVariables();
		// copy constructor
		ps.println("		" + protocol.getLastName() + "(const " + protocol.getLastName() + "& _src_)");
		if (!variables.isEmpty())
			ps.println("			: Protocol( _src_)" + ParamName.getCopyConstructList(variables));
		ps.println("		{}");
		ps.println();

		// varlist constructor
		if (false == variables.isEmpty()) {
			final String beanName = variables.size() == 1 ? ("explicit " + bean.getName()) : bean.getName();
			ps.println("		" + beanName + "(" + ParamName.getParamList(variables) + ")");
			ps.println("			: " + ParamName.getInitConstructList(variables));
			ps.println("		{");
			ps.println("			type = TYPE;");
			ps.println("		}");
			ps.println();
		}

		Marshal.make(bean, ps, "		");
		Unmarshal.make(bean, ps, "		");
		if (bean.isConstType())
			CompareTo.make(bean, ps, "		");
		if (bean.isJSONEnabled())
			JSONMarshal.make(bean, ps, "		");
		ps.println("		" + Netgen.ProtocolBaseClassName + " * clone() const override { return new "
				+ protocol.getLastName() + "(*this); }");
		ps.println();
	}

	void make(File outputInc, File outputSrc) {
		final String name = protocol.getFullName();
		final Bean bean = protocol.getImplementBean();
		final String namespace = service.getFullName() + "." + protocol.getFirstName();
		try (final PrintStream ps = FileOperation.fopen(outputInc, name + ".h")) {
			ps.println();
			ps.println("#pragma once");
			ps.println();

			printIncludes(ps);

			ps.println();
			Xmlgen.begin(namespace, ps);
			ps.println();
			ps.println("	class " + protocol.getLastName() + " : public " + Netgen.ProtocolBaseClassName
					+ (bean.isJSONEnabled() ? ", public limax::JSONMarshal" : ""));
			ps.println("	{");
			ps.println("	public:");
			printDefine(ps);
			ps.println();
			ps.println("	public:");
			ps.println("		virtual void process() override;");
			ps.println();
			ps.println("	};");
			ps.println();
			Xmlgen.end(namespace, ps);
			ps.println();
		}

		if (new File(outputSrc, name + ".cpp").isFile())
			return;
		try (final PrintStream ps = FileOperation.fopen(outputSrc, name + ".cpp")) {
			ps.println();
			ps.println(
					"#include \"../" + Xmlgen.path_xmlgen_inc + "/" + Xmlgen.protocols_path_name + "/" + name + ".h\"");
			ps.println();
			Xmlgen.begin(namespace, ps);
			ps.println();
			ps.println("	void " + protocol.getLastName() + "::process()");
			ps.println("	{");
			ps.println("	}");
			ps.println();
			Xmlgen.end(namespace, ps);
			ps.println();
		}
	}

}
