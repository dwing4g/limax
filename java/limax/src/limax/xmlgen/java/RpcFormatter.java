package limax.xmlgen.java;

import java.io.File;
import java.io.PrintStream;

import limax.xmlgen.FileOperation;
import limax.xmlgen.Parser;
import limax.xmlgen.Rpc;

class RpcFormatter {
	private final String name;
	private final Rpc rpc;
	private final String namespace;
	private final String arg;
	private final String res;

	public RpcFormatter(String base, Rpc rpc) {
		this.rpc = rpc;
		this.name = rpc.getLastName();
		this.namespace = base + "." + rpc.getFirstName();
		this.arg = rpc.getArgumentBean().getFullName();
		this.res = rpc.getResultBean().getFullName();
	}

	void make(File output) {
		output = new File(output, namespace.replace(".", File.separator));
		Parser rp = new Parser();
		if (FileOperation.fparse(output, name + ".java", rp)) {
			rp.verify(namespace + "." + name);
			try (PrintStream ps = FileOperation.fopen(output, name + ".java")) {
				rp.printBeforeImport(ps);
				printImport(ps);
				rp.printImportToDefine(ps);
				printDefine(ps);
				rp.printAfterDefine(ps);
			}
			return;
		}
		try (PrintStream ps = FileOperation.fopen(output, name + ".java")) {
			ps.println();
			ps.println("package " + namespace + ";");
			ps.println();
			printImport(ps);
			ps.println();
			ps.println("public class " + name + " extends limax.net.Rpc<" + arg + ", " + res + "> {");
			ps.println("	@Override");
			ps.println("	protected void onServer() {");
			ps.println("		// request handle");
			ps.println("	}");
			ps.println();
			ps.println("	@Override");
			ps.println("	protected void onClient() {");
			ps.println("		// response handle");
			ps.println("	}");
			ps.println();
			ps.println("	@Override");
			ps.println("	protected void onTimeout() {");
			ps.println("		// client only. when call by submit this method not reached。");
			ps.println("	}");
			ps.println();
			ps.println("	@Override");
			ps.println("	protected void onCancel() {");
			ps.println("		// client only. when asynchronous closed。");
			ps.println("	}");
			ps.println();
			printDefine(ps);
			ps.println("}");
			ps.println();
		}
	}

	private void printImport(PrintStream ps) {
		ps.println("" + Parser.IMPORT_BEGIN);
		ps.println("// {{{ DO NOT EDIT THIS");
		ps.println(rpc.getImplementBean().getComment());
		ps.println("// DO NOT EDIT THIS }}}");
		ps.println("" + Parser.IMPORT_END);
	}

	private void printDefine(PrintStream ps) {
		ps.println("	" + Parser.DEFINE_BEGIN);
		ps.println("	// {{{ DO NOT EDIT THIS");
		ps.println("	public static int TYPE;");
		ps.println();
		ps.println("	public int getType() {");
		ps.println("		return TYPE;");
		ps.println("	}");
		ps.println();
		ps.println("	public " + name + "() {");
		ps.println("		super.setArgument(new " + arg + "());");
		ps.println("		super.setResult(new " + res + "());");
		ps.println("	}");
		ps.println();
		ps.println("	public " + name + "(limax.net.Rpc.Listener<" + arg + ", " + res + "> listener) {");
		ps.println("		super(listener);");
		ps.println("		super.setArgument(new " + arg + "());");
		ps.println("		super.setResult(new " + res + "());");
		ps.println("	}");
		ps.println();
		ps.println("	public " + name + "(" + arg + " argument) {");
		ps.println("		super.setArgument(argument);");
		ps.println("		super.setResult(new " + res + "());");
		ps.println("	}");
		ps.println();
		ps.println("	public " + name + "(" + arg + " argument, limax.net.Rpc.Listener<" + arg + ", " + res
				+ "> listener) {");
		ps.println("		super(listener);");
		ps.println("		super.setArgument(argument);");
		ps.println("		super.setResult(new " + res + "());");
		ps.println("	}");
		ps.println();
		ps.println("	public long getTimeout() {");
		ps.println("		return " + rpc.getTimeout() + ";");
		ps.println("	}");
		ps.println();
		ps.println("	// DO NOT EDIT THIS }}}");
		ps.println("	" + Parser.DEFINE_END);
	}

}
