package limax.xmlgen.java;

import java.io.File;
import java.io.PrintStream;

import limax.xmlgen.FileOperation;
import limax.xmlgen.Parser;
import limax.xmlgen.Protocol;

class ProtocolFormatter {
	private final String name;
	private final Protocol protocol;
	private final String namespace;
	private final BeanFormatter beanformatter;

	public ProtocolFormatter(String base, Protocol protocol) {
		this.name = protocol.getLastName();
		this.namespace = base + "." + protocol.getFirstName();
		this.protocol = protocol;
		this.beanformatter = new BeanFormatter(protocol.getImplementBean());
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
			ps.println("public class " + name + " extends limax.net.Protocol"
					+ (protocol.getImplementBean().isJSONEnabled() ? " implements limax.codec.JSONMarshal" : "")
					+ " {");
			ps.println("	@Override");
			ps.println("	public void process() {");
			ps.println("		// protocol handle");
			ps.println("	}");
			ps.println();
			printDefine(ps);
			ps.println();
			ps.println("}");
			ps.println();
		}
	}

	private void printImport(PrintStream ps) {
		ps.println("" + Parser.IMPORT_BEGIN);
		ps.println("// {{{ DO NOT EDIT THIS");
		ps.println(protocol.getImplementBean().getComment());
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
		beanformatter.printDefine(ps, "", true);
		ps.println("	// DO NOT EDIT THIS }}}");
		ps.println("	" + Parser.DEFINE_END);
	}

}
