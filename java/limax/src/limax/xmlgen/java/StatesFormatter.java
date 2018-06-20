package limax.xmlgen.java;

import java.io.File;
import java.io.PrintStream;
import java.util.Collection;

import limax.util.StringUtils;
import limax.xmlgen.FileOperation;
import limax.xmlgen.Manager;
import limax.xmlgen.Protocol;
import limax.xmlgen.Rpc;
import limax.xmlgen.Service;
import limax.xmlgen.State;

final class StatesFormatter {
	private final Manager manager;
	private final String namespace;

	StatesFormatter(Service service, Manager manager) {
		this.manager = manager;
		this.namespace = service.getFullName();
	}

	void makeStates(File output) {
		output = new File(output, namespace.replace(".", File.separator));

		try (final PrintStream ps = FileOperation.fopen(new File(output, "states"), manager.getName() + ".java")) {

			ps.println("package " + namespace + ".states;");
			ps.println();
			ps.println("import limax.net.State;");
			ps.println();
			ps.println("public final class " + manager.getName() + " {");
			ps.println();

			final Collection<State> sortstates = manager.getStates();

			if (manager.isClient() && sortstates.size() == 1) {
				State state = sortstates.iterator().next();
				if (state.getNamespaces().size() == 1 && state.getNamespaces().get(0).getPvid() != 0) {
					String outstatename = StringUtils.upper1(state.getName());
					ps.println("	private static State create" + outstatename + "(int pvid) {");
					ps.println("		final State state = new State();");
					for (final Protocol p : state.getProtocols())
						ps.println("		state.addStub(" + namespace + "." + p.getFullName() + ".class, " + namespace
								+ "." + p.getFullName() + ".TYPE = (pvid << 8)|" + p.getType() + ", " + p.getMaxsize()
								+ ");");
					ps.println("		return state;");
					ps.println("	}");
					ps.println();
					ps.println("	public static State getDefaultState(int pvid) {");
					ps.println("		return create" + outstatename + "(pvid);");
					ps.println("	}");
					ps.println("}");
					ps.println();
					return;
				}
			}

			for (final State state : sortstates) {
				final String outstatename = StringUtils.upper1(state.getName());
				ps.println("	private static State create" + outstatename + "() {");
				ps.println("		final State state = new State();");
				for (final Protocol p : state.getProtocols())
					ps.println("		state.addStub(" + namespace + "." + p.getFullName() + ".class, " + namespace
							+ "." + p.getFullName() + ".TYPE=" + p.getType() + ", " + p.getMaxsize() + ");");
				for (final Rpc p : state.getRpcs())
					ps.println("		state.addStub(" + namespace + "." + p.getFullName() + ".class, " + namespace
							+ "." + p.getFullName() + ".TYPE=" + p.getType() + ", " + p.getMaxsize() + ");");
				ps.println("		return state;");
				ps.println("	}");
				ps.println();
			}

			for (final State state : sortstates) {
				final String outstatename = StringUtils.upper1(state.getName());
				ps.println("	public final static State " + state.getName() + " = create" + outstatename + "();");
			}
			ps.println();

			ps.println("	public static State getDefaultState() {");
			ps.println("		return " + manager.getInitStateName() + ";");
			ps.println("	}");

			ps.println("}");
			ps.println();
		}
	}

}
