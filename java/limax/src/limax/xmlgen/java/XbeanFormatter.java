package limax.xmlgen.java;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import limax.xmlgen.Bean;
import limax.xmlgen.Enum;
import limax.xmlgen.Variable;
import limax.xmlgen.Xbean;
import limax.xmlgen.Xbean.DynamicVariable;

class XbeanFormatter {
	public static void make(Xbean xbean) {
		try (PrintStream ps = Zdbgen.openXBeanFile(xbean.getName())) {
			new XbeanFormatter(xbean, ps).make();
		}
	}

	private final Xbean xbean;
	private final PrintStream ps;
	private final boolean isAny;
	private final List<Variable> variables;

	private XbeanFormatter(Xbean xbean, PrintStream ps) {
		this.xbean = xbean;
		this.ps = ps;
		this.isAny = xbean.isAny();
		this.variables = xbean.getVariables();
		if (!isAny)
			xbean.depends().forEach(type -> {
				if (type instanceof Bean)
					throw new RuntimeException(
							"Xbean '" + xbean.getName() + "' CAN NOT depend bean type '" + type.getName() + "'");
			});
	}

	private void make() {
		String cls = xbean.getName();
		ps.println("package xbean;");
		ps.println();
		ps.println("import limax.codec.OctetsStream;");
		ps.println("import limax.codec.MarshalException;");
		List<String> interfaces = new ArrayList<>();
		if (xbean.attachment() != null) {
			ps.println("import limax.codec.StringStream;");
			ps.println("import limax.codec.StringMarshal;");
			interfaces.add("StringMarshal");
		}
		if (xbean.isJSONEnabled()) {
			ps.println("import limax.codec.JSONBuilder;");
			ps.println("import limax.codec.JSONMarshal;");
			interfaces.add("JSONMarshal");
		}
		ps.println();
		ps.println("public final class " + cls + " extends limax.zdb.XBean"
				+ (interfaces.isEmpty() ? "" : " implements " + String.join(",", interfaces)) + " {");
		List<Enum> enums = xbean.getEnums();
		enums.forEach(e -> Declare.make(e, ps, "    "));
		if (!enums.isEmpty())
			ps.println();
		variables.forEach(var -> Declare.make(var, Declare.Type.PRIVATE, ps, "    "));
		ps.println();
		ps.println("    " + cls + "(limax.zdb.XBean _xp_, String _vn_) {");
		ps.println("        super(_xp_, _vn_);");
		variables.forEach(var -> Construct.make(var, ps, "        "));
		ps.println("	}");
		ps.println();
		ps.println("	public " + cls + "() {");
		ps.println("		this(null, null);");
		ps.println("	}");
		ps.println();

		ps.println("	public " + cls + "(" + cls + " _o_) {");
		ps.println("		this(_o_, null, null);");
		ps.println("	}");
		ps.println();
		ps.println("	" + cls + "(" + cls + " _o_, limax.zdb.XBean _xp_, String _vn_) {");
		ps.println("		super(_xp_, _vn_);");

		Zdbgen.verify(ps, "		", "_o_", "_o_." + cls, true);
		if (isAny) {
			ps.println("		throw new UnsupportedOperationException();");
			ps.println("	}");
			ps.println();
		} else {
			variables.forEach(var -> VarDeepCopy.make(var, ps, "        "));
			ps.println("	}");
			ps.println();
		}

		ps.println("	public void copyFrom(" + cls + " _o_) {");
		Zdbgen.verify(ps, "		", "_o_", "copyFrom" + cls, true);
		Zdbgen.verify(ps, "		", "this", "copyTo" + cls, false);
		if (isAny) {
			ps.println("		throw new UnsupportedOperationException();");
			ps.println("	}");
			ps.println();
		} else {
			variables.forEach(var -> VarCopyFrom.make(var, ps, "        "));
			ps.println("	}");
			ps.println();
		}

		Collection<Variable> svars = xbean.getStaticVariables();
		Collection<DynamicVariable> dvars = xbean.isDynamic() ? xbean.getDynamicVariables() : null;

		// marshal implement
		Collection<Runnable> delayed;
		ps.println("	@Override");
		ps.println("	public final OctetsStream marshal(OctetsStream _os_) {");
		if (isAny) {
			ps.println("		throw new UnsupportedOperationException();");
			delayed = Collections.emptyList();
		} else {
			Zdbgen.verify(ps, "		", "this", "marshal", true);
			delayed = Marshal.make(svars, dvars, ps, "	");
		}
		ps.println("    }");
		ps.println();
		delayed.forEach(Runnable::run);

		if (xbean.attachment() != null) {
			ps.println("	@Override");
			ps.println("	public final StringStream marshal(StringStream _os_) {");
			if (isAny) {
				ps.println("		throw new UnsupportedOperationException();");
			} else {
				Zdbgen.verify(ps, "        ", "this", "marshal", true);
				ps.println("		_os_.append(\"L\");");
				variables.forEach(var -> StringMarshal.make(var, ps, "		"));
				ps.println("		return _os_.append(\":\");");
			}
			ps.println("    }");
			ps.println();
		}

		if (xbean.isJSONEnabled()) {
			ps.println("	@Override");
			ps.println("	public final JSONBuilder marshal(JSONBuilder _os_) {");
			Zdbgen.verify(ps, "        ", "this", "marshal", true);
			ps.println("		_os_.begin();");
			variables.stream().filter(var -> var.isJSONEnabled()).forEach(var -> JSONMarshal.make(var, ps, "		"));
			ps.println("		return _os_.end();");
			ps.println("    }");
			ps.println();
		}

		ps.println("	@Override");
		ps.println("	public final OctetsStream unmarshal(OctetsStream _os_) throws MarshalException {");
		if (isAny) {
			ps.println("		throw new UnsupportedOperationException();");
			delayed = Collections.emptyList();
		} else {
			Zdbgen.verify(ps, "		", "this", "unmarshal", false);
			delayed = Unmarshal.make(svars, dvars, xbean.getName(), true, ps, "	");
		}
		ps.println("	}");
		ps.println();
		delayed.forEach(Runnable::run);

		// getter setter
		variables.forEach(var -> VarGetter.make(var, ps, "	"));
		variables.forEach(var -> VarSetter.make(var, ps, "	"));
		// equals
		ps.println("	@Override");
		ps.println("	public final boolean equals(Object _o1_) {");
		Zdbgen.verify(ps, "		", "this", "equals", true);
		if (!variables.isEmpty()) {
			ps.println("		" + cls + " _o_ = null;");
			ps.println("		if ( _o1_ instanceof " + cls + " ) _o_ = (" + cls + ")_o1_;");
			ps.println("		else return false;");
			variables.forEach(var -> Equals.make(var, ps, "		"));
		}
		ps.println("		return true;");
		ps.println("	}");
		ps.println();
		Hashcode.make(xbean, ps, "	");
		Trace.make(xbean, ps, "	");
		if (xbean.isConstType())
			CompareTo.make(xbean, ps, "	");
		ps.println("}");
	}
}
