package limax.xmlgen.java;

import java.io.File;
import java.io.PrintStream;

import limax.xmlgen.FileOperation;
import limax.xmlgen.Xbean;

class ViewXbeanFormatter {
	private final Xbean bean;
	private final String name;

	public ViewXbeanFormatter(Xbean bean) {
		this.bean = bean;
		this.name = bean.getLastName();
	}

	public void make(File output) {
		output = new File(output, "xbean");
		try (PrintStream ps = FileOperation.fopen(output, name + ".java")) {
			ps.println("package xbean;");
			ps.println();
			ps.println("public class " + bean.getName() + " implements limax.codec.Marshal"
					+ (bean.isJSONEnabled() ? ", limax.codec.JSONMarshal" : "") + " {");
			printDefine(ps);
			ps.println("}");
			ps.println();
		}
	}

	public void printDefine(PrintStream ps) {
		Declare.make(bean.getEnums(), bean.getVariables(), Declare.Type.PUBLIC, ps, "	");
		Construct.make(bean, ps, "	");
		Marshal.make(bean, ps, "	");
		Unmarshal.make(bean, ps, "	");
		if (bean.isConstType())
			CompareTo.make(bean, ps, "	");
		if (bean.isJSONEnabled())
			JSONMarshal.make(bean, ps, "	");
		Equals.make(bean, ps, "	");
		Hashcode.make(bean, ps, "	");
	}
}
