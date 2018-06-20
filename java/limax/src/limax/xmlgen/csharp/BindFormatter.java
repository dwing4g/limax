package limax.xmlgen.csharp;

import java.io.PrintStream;

import limax.xmlgen.Bind;

class BindFormatter {
	private final Bind bind;

	public BindFormatter(Bind bind) {
		this.bind = bind;
	}

	public void make(PrintStream ps) {
		if (bind.isFullBind())
			return; // full bean import need not generate
		ps.println("		public sealed class __" + bind.getName());
		ps.println("		{");
		printDefine(ps);
		ps.println("		}");
	}

	public void printDefine(PrintStream ps) {
		Define.make(bind, ps, "			");
		Construct.make(bind, ps, "			");
		Marshal.make(bind, ps, "			");
		Unmarshal.make(bind, ps, "			");
		Equals.make(bind, ps, "			");
		Hashcode.make(bind, ps, "			");
	}
}
