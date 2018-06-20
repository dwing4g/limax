package limax.xmlgen.java;

import java.io.PrintStream;

import limax.util.StringUtils;
import limax.xmlgen.Bean;
import limax.xmlgen.Bind;
import limax.xmlgen.Cbean;
import limax.xmlgen.Main;
import limax.xmlgen.Type;
import limax.xmlgen.TypeAny;
import limax.xmlgen.TypeBinary;
import limax.xmlgen.TypeBoolean;
import limax.xmlgen.TypeByte;
import limax.xmlgen.TypeDouble;
import limax.xmlgen.TypeFloat;
import limax.xmlgen.TypeInt;
import limax.xmlgen.TypeList;
import limax.xmlgen.TypeLong;
import limax.xmlgen.TypeMap;
import limax.xmlgen.TypeSet;
import limax.xmlgen.TypeShort;
import limax.xmlgen.TypeString;
import limax.xmlgen.TypeVector;
import limax.xmlgen.View;
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;
import limax.xmlgen.java.Viewgen.Marshals;

final class Setbind implements Visitor {
	private final Bind bind;
	private final PrintStream ps;
	private final boolean fullbind;
	private final int varIndex;
	private final String bindtype;
	private final Marshals marshals;
	private final boolean immutable;

	private Setbind(PrintStream ps, View view, Bind bind, ViewFormatter vf, Marshals marshals, boolean immutable) {
		this.ps = ps;
		this.bind = bind;
		this.varIndex = vf.getMemberIndex(bind);
		this.fullbind = bind.isFullBind();
		this.bindtype = vf.getServerBindType(view, bind);
		this.marshals = marshals;
		this.immutable = immutable;
	}

	public static void make(PrintStream ps, View view, Bind bind, ViewFormatter vf, Marshals marshals,
			boolean immutable) {
		bind.getValueType().accept(new Setbind(ps, view, bind, vf, marshals, immutable));
	}

	private void sync(String var) {
		String space;
		if (bind.isClip()) {
			ps.println("		if (permit" + StringUtils.upper1(bind.getName()) + "(" + var + ")) {");
			space = "	";
		} else
			space = "";
		if (immutable)
			ps.println(space + "		ViewDataCollector.Data _v_ = new ViewDataCollector.Data(" + marshals.get(bind)
					+ "(" + var
					+ "), " + (Main.scriptSupport
							? "isScriptEnabled() ? " + marshals.get(bind) + "(\"\", " + var + ") : \"\"" : "\"\"")
					+ ");");
		else
			ps.println(space + "		ViewDataCollector.Data _v_ = new ViewDataCollector.MutableData("
					+ marshals.get(bind) + "(" + var
					+ "), " + (Main.scriptSupport
							? "isScriptEnabled() ? " + marshals.get(bind) + "(\"\", " + var + ") : \"\"" : "\"\"")
					+ ", _f_);");
		ps.println(space + "		schedule(() -> update((byte)" + varIndex + ", _v_));");
		if (bind.isClip())
			ps.println("		}");
		ps.println("	}");
		ps.println();
	}

	private void head0(String name) {
		ps.println("	private void set" + StringUtils.upper1(bind.getName()) + "(" + name + " _p_"
				+ (immutable ? "" : ", List<ViewDataCollector.Field> _f_") + ") {");
	}

	private void head1(Type type) {
		ps.println("	private void set" + StringUtils.upper1(bind.getName()) + "(" + TypeName.getBoxingName(type)
				+ " _p_" + (immutable ? "" : ", List<ViewDataCollector.Field> _f_") + ") {");
		sync("_p_");
	}

	@Override
	public void visit(Cbean type) {
		head0(type.getFullName());
		if (fullbind) {
			sync("_p_");
		} else {
			ps.println("		" + bindtype + " _q_ = _p_ == null ? null : new " + bindtype + "(_p_);");
			sync("_q_");
		}
	}

	@Override
	public void visit(Xbean type) {
		head0(type.getFullName());
		if (fullbind) {
			sync("_p_");
		} else {
			ps.println("		" + bindtype + " _q_ = _p_ == null ? null : new " + bindtype + "(_p_);");
			sync("_q_");
		}
	}

	@Override
	public void visit(TypeString type) {
		head1(type);
	}

	@Override
	public void visit(TypeBinary type) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(TypeBoolean type) {
		head1(type);
	}

	@Override
	public void visit(TypeByte type) {
		head1(type);
	}

	@Override
	public void visit(TypeShort type) {
		head1(type);
	}

	@Override
	public void visit(TypeInt type) {
		head1(type);
	}

	@Override
	public void visit(TypeLong type) {
		head1(type);
	}

	@Override
	public void visit(TypeFloat type) {
		head1(type);
	}

	@Override
	public void visit(TypeDouble type) {
		head1(type);
	}

	@Override
	public void visit(TypeList type) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(TypeVector type) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(TypeSet type) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(TypeMap type) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(TypeAny type) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(Bean type) {
		throw new UnsupportedOperationException();
	}

}
