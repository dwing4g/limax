package limax.xmlgen.cpp;

import java.io.PrintStream;

import limax.xmlgen.Bind;
import limax.xmlgen.Variable;

class BindFormatter {
	private final Bind bind;

	public BindFormatter(Bind bind) {
		this.bind = bind;
	}

	public void make(PrintStream ps) {
		if (bind.isFullBind())
			return; // full bean import need not generate
		ps.println("		class " + bind.getName() + " : public limax::Marshal");
		ps.println("		{");
		printDefine(ps);
		ps.println("		};");
		ps.println();
	}

	public void printDefine(PrintStream ps) {
		// declare variables
		ps.println("		public:");
		for (Variable v : bind.getVariables()) {
			ps.println("			" + TypeName.getName(v.getType()) + " " + v.getName() + "; " + v.getComment());
		}
		ps.println("");

		Construct.make(bind, ps, "			");
		Marshal.make(bind, ps, "			");
		Unmarshal.make(bind, ps, "			");
		Equals.make(bind, ps, "			");
	}
}
