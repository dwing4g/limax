package limax.xmlgen.csharp;

import java.io.File;
import java.io.PrintStream;
import java.util.Collection;

import limax.util.StringUtils;
import limax.xmlgen.FileOperation;
import limax.xmlgen.Main;
import limax.xmlgen.Manager;
import limax.xmlgen.Protocol;
import limax.xmlgen.Service;
import limax.xmlgen.State;

final class StatesFormatter {
	private final Manager manager;
	private final String namespace;

	StatesFormatter(Service service, Manager manager) {
		this.manager = manager;
		this.namespace = service.getFullName();
	}

	void makeStates() {
		final File output = new File(Main.outputPath, Xmlgen.path_xmlgen);

		try (final PrintStream ps = FileOperation.fopen(output, manager.getName().toLowerCase() + ".states.cs")) {
			BeanFormatter.printCommonInclude(ps);
			final Collection<State> sortstates = manager.getStates();
			ps.println("namespace " + namespace + ".states");
			ps.println("{");
			ps.println("	static public class " + manager.getName());
			ps.println("	{");
			if (manager.isClient() && sortstates.size() == 1) {
				State state = sortstates.iterator().next();
				if (state.getNamespaces().size() == 1 && state.getNamespaces().get(0).getPvid() != 0) {
					String outstatename = StringUtils.upper1(state.getName());
					ps.println("		static public limax.net.State create" + outstatename + "(int pvid) {");
					ps.println("			limax.net.State state = new limax.net.State();");
					for (final Protocol p : state.getProtocols())
						ps.println("			state.addStub(typeof(" + namespace + "." + p.getFullName() + "), "
								+ namespace + "." + p.getFullName() + ".TYPE=(pvid << 8)|" + p.getType() + ", "
								+ p.getMaxsize() + ");");
					ps.println("			return state;");
					ps.println("		}");
					ps.println("		static public limax.net.State getDefaultState(int pvid) {");
					ps.println(
							"			return create" + StringUtils.upper1(manager.getInitStateName()) + "(pvid);;");
					ps.println("		}");
					ps.println("	}");
					ps.println("}");
					return;
				}
			}
			for (final State state : sortstates) {
				final String outstatename = StringUtils.upper1(state.getName());
				ps.println("		static public limax.net.State create" + outstatename + "() {");
				ps.println("			limax.net.State state = new limax.net.State();");
				for (final Protocol p : state.getProtocols())
					ps.println(
							"			state.addStub(typeof(" + namespace + "." + p.getFullName() + "), " + namespace
									+ "." + p.getFullName() + ".TYPE=" + p.getType() + ", " + p.getMaxsize() + ");");
				ps.println("			return state;");
				ps.println("		}");
			}
			for (final State state : sortstates) {
				final String outstatename = StringUtils.upper1(state.getName());
				ps.println(
						"		static public limax.net.State " + state.getName() + " = create" + outstatename + "();");
			}
			ps.println("		static public limax.net.State getDefaultState() {");
			ps.println("			return " + manager.getInitStateName() + ";");
			ps.println("		}");
			ps.println("	}");
			ps.println("}");
		}
	}

}
