package limax.xmlgen.csharp;

import java.io.File;
import java.io.PrintStream;

import limax.xmlgen.FileOperation;
import limax.xmlgen.Protocol;
import limax.xmlgen.Service;

class ProtocolFormatter {
	private final Service service;
	private final Protocol protocol;

	public ProtocolFormatter(Service service, Protocol protocol) {
		this.service = service;
		this.protocol = protocol;
	}

	private void printDefine(PrintStream ps) {
		// define
		ps.println("		public static int TYPE;");
		ps.println("		override public int getType()");
		ps.println("		{");
		ps.println("			return TYPE;");
		ps.println("		}");
		BeanFormatter.declareEnums(ps, protocol.getImplementBean().getEnums());
		Define.make(protocol, ps, "		");
		Construct.make(protocol, ps, "		");
		ConstructWithParam.make(protocol, ps, "		");
		Marshal.make(protocol, ps, "		");
		if (protocol.getImplementBean().isJSONEnabled())
			JSONMarshal.make(protocol.getImplementBean(), ps, "		");
		Unmarshal.make(protocol, ps, "		");
	}

	void make(File outputGen, File outputSrc) {
		final String name = protocol.getFullName();
		final String namespace = service.getFullName() + "." + protocol.getFirstName();
		try (final PrintStream ps = FileOperation.fopen(outputGen, name + ".cs")) {
			BeanFormatter.printCommonInclude(ps);
			ps.println("namespace " + namespace);
			ps.println("{");
			ps.println("	public sealed partial class " + protocol.getLastName() + " : limax.net.Protocol"
					+ (protocol.getImplementBean().isJSONEnabled() ? ", JSONMarshal" : ""));
			ps.println("	{");
			printDefine(ps);
			ps.println("	}");
			ps.println("}");
		}
		if (new File(outputSrc, name + ".cs").isFile())
			return;
		try (final PrintStream ps = FileOperation.fopen(outputSrc, name + ".cs")) {
			ps.println("namespace " + namespace);
			ps.println("{");
			ps.println("	public sealed partial class " + protocol.getLastName() + " ");
			ps.println("	{");
			ps.println("		override public void process() {}");
			ps.println("	}");
			ps.println("}");
		}
	}

}
