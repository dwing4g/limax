package limax.xmlgen.java;

import java.io.File;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import limax.xmlgen.Cbean;
import limax.xmlgen.Enum;
import limax.xmlgen.Main;
import limax.xmlgen.Variable;

class CbeanFormatter {

	static void make(Cbean cbean, File genDir) {
		if (cbean.touch())
			return;
		boolean noverifySaved = Main.zdbNoverify;
		Main.zdbNoverify = true;
		_make(cbean, genDir);
		Main.zdbNoverify = noverifySaved;
	}

	private static void _make(Cbean cbean, File genDir) {
		String classname = cbean.getLastName();
		try (PrintStream ps = Zdbgen.openCBeanFile(genDir, classname)) {
			List<Variable> variables = cbean.getVariables();
			List<Enum> enums = cbean.getEnums();
			ps.println();
			ps.println("package cbean;");
			ps.println();
			ps.println("public class " + classname + " implements limax.codec.Marshal, "
					+ (cbean.attachment() == null ? "" : "limax.codec.StringMarshal, ") + "Comparable<" + classname
					+ ">" + (cbean.isJSONEnabled() ? ", limax.codec.JSONMarshal" : "") + " {");
			ps.println();
			Declare.make(enums, variables, Declare.Type.PRIVATE, ps, "	");
			Construct.make(cbean, ps, "	");
			ConstructWithParam.make(variables, classname, ps, "	");
			variables.forEach(var -> VarGetter.make(var, ps, "	"));
			Marshal.make(cbean, ps, "	");
			if (cbean.attachment() != null)
				StringMarshal.make(cbean, ps, "	");
			if (cbean.isJSONEnabled())
				JSONMarshal.make(cbean, ps, "	");
			Unmarshal.make(cbean, ps, "	");
			CompareTo.make(cbean, ps, "	");
			Equals.make(cbean, ps, "	");
			Hashcode.make(cbean, ps, "	");
			if (cbean.isBuilderEnabled())
				makeBuilder(ps, cbean);
			ps.println("}");
		}
	}

	private static void makeBuilder(PrintStream ps, Cbean cbean) {
		ps.println();
		ps.println("	public static final class Builder {");
		ps.println();
		final List<Variable> variables = cbean.getVariables();
		Declare.make(Collections.emptyList(), variables, Declare.Type.PRIVATE, ps, "		");
		ps.println("		private Builder() {");
		ps.println("		}");
		ps.println();
		ps.println("		private Builder(cbean." + cbean.getLastName() + " bean) {");
		variables.forEach(var -> ps.println("			this." + var.getName() + " = bean." + var.getName() + ";"));
		ps.println("		}");
		ps.println();
		variables.forEach(var -> {
			ps.println("		public Builder " + var.getName() + "(" + TypeName.getName(var.getType()) + "  v) {");
			ps.println("			this." + var.getName() + " = v;");
			ps.println("			return this;");
			ps.println("		}");
			ps.println();
		});
		ps.println("		public cbean." + cbean.getLastName() + " build() {");
		ps.print("			return new cbean." + cbean.getLastName() + "(");
		ps.print(variables.stream().map(e -> "this." + e.getName()).collect(Collectors.joining(", ")));
		ps.println(");");
		ps.println("		}");
		ps.println("	}");
		ps.println();

		ps.println("	public static Builder builder() {");
		ps.println("		return new Builder();");
		ps.println("	}");
		ps.println();
		ps.println("	public static Builder builder(cbean." + cbean.getLastName() + " bean) {");
		ps.println("		return new Builder(bean);");
		ps.println("	}");
		ps.println();
	}

}
