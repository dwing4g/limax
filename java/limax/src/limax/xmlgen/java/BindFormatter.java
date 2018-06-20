package limax.xmlgen.java;

import java.io.PrintStream;

import limax.xmlgen.Bind;

class BindFormatter {
	private final Bind bind;
	private final boolean genServer;

	public BindFormatter(Bind bind, boolean genServer) {
		this.bind = bind;
		this.genServer = genServer;
	}

	public void make(PrintStream ps) {
		if (bind.isFullBind())
			return; // full bean import need not generate
		ps.println("	" + (genServer ? (bind.isClip() ? "protected" : "private") : "public") + " static class "
				+ bind.getName() + " implements limax.codec.Marshal"
				+ (bind.attachment() == null ? "" : ", limax.codec.StringMarshal") + " {");
		printDefine(ps);
		ps.println("	}");
		ps.println();
	}

	public void printDefine(PrintStream ps) {
		if (genServer) {
			bind.getVariables().forEach(v -> ps.println("		public " + TypeName.getGetterName(v.getType()) + " "
					+ v.getName() + "; " + v.getComment()));
			ps.println("");
			VarShallowCopy.make(bind, ps, "		");
			Marshal.make(bind.getVariables(), ps, "		");
			if (bind.attachment() != null)
				StringMarshal.make(bind.getVariables(), ps, "		");
			ps.println("		@Override");
			ps.println(
					"		public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {");
			ps.println("			throw new UnsupportedOperationException();");
			ps.println("		}");
		} else {
			bind.getVariables().forEach(v -> ps.println(
					"		public " + TypeName.getName(v.getType()) + " " + v.getName() + "; " + v.getComment()));
			ps.println("");
			Construct.make(bind, ps, "		");
			ps.println("		@Override");
			ps.println("		public limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {");
			ps.println("			throw new UnsupportedOperationException();");
			ps.println("		}");
			Unmarshal.make(bind, ps, "		");
			Equals.make(bind, ps, "		");
		}
	}
}
