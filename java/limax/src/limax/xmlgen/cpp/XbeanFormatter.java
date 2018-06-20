package limax.xmlgen.cpp;

import java.io.File;
import java.io.PrintStream;

import limax.xmlgen.FileOperation;
import limax.xmlgen.Xbean;

public class XbeanFormatter {
	private final Xbean bean;

	public XbeanFormatter(Xbean bean) {
		this.bean = bean;
	}

	public void make(File output) {
		try (final PrintStream ps = FileOperation.fopen(output, bean.getFullName() + ".h")) {
			ps.println("#pragma once");
			ps.println();
			BeanFormatter.printCommonInclude(ps);
			for (String inc : Include.includes(bean, ""))
				ps.println(inc);
			ps.println();

			Xmlgen.begin(bean.getFirstName(), ps);
			ps.println();
			ps.println("	class " + bean.getName() + " : public " + BeanFormatter.baseClassName
					+ (bean.isJSONEnabled() ? ", public limax::JSONMarshal" : ""));
			ps.println("	{");
			printDefine(ps);
			ps.println("	};");
			ps.println();
			Xmlgen.end(bean.getFirstName(), ps);
			ps.println();
		}
	}

	public void printDefine(PrintStream ps) {
		ps.println("	public:");
		BeanFormatter.declareEnums(ps, bean.getEnums());
		BeanFormatter.declareVariables(ps, bean.getVariables(), "		");

		ps.println("	public:");
		Construct.make(bean, ps, "		");
		BeanFormatter.declareInitConstruct(ps, bean.getName(), bean.getVariables(), "		");

		ps.println("	public:");
		Marshal.make(bean, ps, "		");
		Unmarshal.make(bean, ps, "		");
		if (bean.isConstType())
			CompareTo.make(bean, ps, "		");
		if (bean.isJSONEnabled())
			JSONMarshal.make(bean, ps, "		");
		Equals.make(bean, ps, "		");
		Hashcode.make(bean, ps, "		");
	}
}
