package limax.xmlgen.java;

import java.io.File;
import java.io.PrintStream;

import limax.xmlgen.Bean;
import limax.xmlgen.FileOperation;

class BeanFormatter {
	private final Bean bean;
	private final String name;
	private final String namespace;

	public BeanFormatter(Bean bean) {
		this.bean = bean;
		this.name = bean.getLastName();
		this.namespace = bean.getFirstName();
	}

	public void make(File output) {
		output = new File(output, namespace.replace(".", File.separator));
		try (PrintStream ps = FileOperation.fopen(output, name + ".java")) {
			ps.println("package " + namespace + ";");
			ps.println();
			printImport(ps);
			ps.println("public class " + name + " implements limax.codec.Marshal"
					+ (bean.attachment() == null ? " " : ", limax.codec.StringMarshal")
					+ (bean.isConstType() ? (", Comparable<" + name + ">") : "")
					+ (bean.isJSONEnabled() ? ", limax.codec.JSONMarshal" : "") + " {");
			printDefine(ps, "", false);
			ps.println("}");
			ps.println();
		}
	}

	static void printComment(PrintStream ps, String comment) {
		if (!comment.isEmpty()) {
			ps.print("/** ");
			ps.println(comment);
			ps.println("*/");
		}
	}

	private void printImport(PrintStream ps) {
		ps.println(bean.getComment());
	}

	public void printDefine(PrintStream ps, String prefix, boolean protocol) {
		Declare.make(bean.getEnums(), bean.getVariables(), Declare.Type.PUBLIC, ps, prefix + "    ");
		Construct.make(bean, ps, prefix + "	");
		ConstructWithParam.make(bean, ps, prefix + "	");
		Marshal.make(bean, ps, prefix + "	");
		if (bean.attachment() != null)
			StringMarshal.make(bean, ps, prefix + "	");
		if (bean.isJSONEnabled())
			JSONMarshal.make(bean, ps, prefix + "	");
		Unmarshal.make(bean, ps, prefix + "	");
		Trace.make(bean, ps, prefix + "	");
		if (!protocol) {
			Equals.make(bean, ps, prefix + "	");
			Hashcode.make(bean, ps, prefix + "	");
			if (bean.isConstType())
				CompareTo.make(bean, ps, prefix + "	");
		}
	}

}
