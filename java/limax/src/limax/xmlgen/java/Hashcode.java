package limax.xmlgen.java;

import java.io.PrintStream;
import java.util.Collection;

import limax.xmlgen.Bean;
import limax.xmlgen.Cbean;
import limax.xmlgen.Main;
import limax.xmlgen.Monitorset;
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
import limax.xmlgen.Variable;
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;

class Hashcode implements Visitor {

	private static void make(Collection<? extends Variable> variables, PrintStream ps, String prefix) {
		ps.println(prefix + "@Override");
		ps.println(prefix + "public int hashCode() {");
		ps.println(prefix + "	int _h_ = 0;");
		variables.forEach(var -> {
			Hashcode e = new Hashcode(var.getName());
			var.getType().accept(e);
			ps.println(prefix + "	_h_ += _h_ * 31 + " + e.getText() + ";");
		});
		ps.println(prefix + "	return _h_;");
		ps.println(prefix + "}");
		ps.println();
	}

	public static void make(Cbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Xbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Bean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Monitorset cts, PrintStream ps, String prefix) {
		make(cts.getKeys(), ps, prefix);
	}

	private final String varname;
	private String text;

	public String getText() {
		return text;
	}

	private Hashcode(String varname) {
		this.varname = "this." + varname;
	}

	private void model0() {
		text = varname + ".hashCode()";
	}

	@Override
	public void visit(TypeByte type) {
		text = "(int)" + varname;
	}

	@Override
	public void visit(TypeInt type) {
		text = varname;
	}

	@Override
	public void visit(TypeShort type) {
		text = varname;
	}

	@Override
	public void visit(TypeLong type) {
		text = "(int)(" + varname + " ^ (" + varname + " >>> 32))";
	}

	@Override
	public void visit(TypeBinary type) {
		if (Main.isMakingZdb)
			text = "java.util.Arrays.hashCode(" + varname + ")";
		else
			text = varname + ".hashCode()";
	}

	@Override
	public void visit(TypeString type) {
		model0();
	}

	@Override
	public void visit(TypeList type) {
		model0();
	}

	@Override
	public void visit(TypeVector type) {
		model0();
	}

	@Override
	public void visit(TypeSet type) {
		model0();
	}

	@Override
	public void visit(TypeMap type) {
		model0();
	}

	@Override
	public void visit(TypeFloat type) {
		text = "Float.floatToIntBits(" + varname + ")";
	}

	@Override
	public void visit(TypeDouble type) {
		text = "Double.valueOf(" + varname + ").hashCode()";
	}

	@Override
	public void visit(Bean type) {
		model0();
	}

	@Override
	public void visit(Cbean type) {
		model0();
	}

	@Override
	public void visit(Xbean type) {
		model0();
	}

	@Override
	public void visit(TypeBoolean type) {
		text = "(" + varname + " ? 1231 : 1237" + ")";
	}

	@Override
	public void visit(TypeAny type) {
		text = "(" + varname + " == null ? 0 : " + varname + ".hashCode())";
	}

}
