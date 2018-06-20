package limax.xmlgen.java;

import java.io.PrintStream;

import limax.util.StringUtils;
import limax.xmlgen.Bean;
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
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;

public class Define implements Visitor {

	private static String useXbeanParent_VarName;
	private static String oldUseXbeanParent_VarName;
	private static String xbeanParent;

	private static boolean initial;
	private static boolean oldInitial;

	public static void beginXbeanParent_VarName(String name, String parent) {
		oldUseXbeanParent_VarName = useXbeanParent_VarName;
		useXbeanParent_VarName = name;
		xbeanParent = parent;
	}

	public static void beginXbeanParent_VarName(String name) {
		beginXbeanParent_VarName(name, "this");
	}

	public static void endXbeanParent_VarName() {
		useXbeanParent_VarName = oldUseXbeanParent_VarName;
	}

	public static void beginInitial(boolean init) {
		oldInitial = initial;
		initial = init;
	}

	public static void endInitial() {
		initial = oldInitial;
	}

	public static void make(Type type, String varname, PrintStream ps, String prefix) {
		type.accept(new Define(varname, ps, prefix));
	}

	private final String varname;
	private final PrintStream ps;
	private final String prefix;

	private Define(String varname, PrintStream ps, String prefix) {
		this.varname = varname;
		this.ps = ps;
		this.prefix = prefix;
	}

	private void model0(Type type, String init) {
		if (initial)
			ps.println(prefix + TypeName.getName(type) + " " + varname + " = " + init + ";");
		else
			ps.println(prefix + TypeName.getName(type) + " " + varname + ";");
	}

	@Override
	public void visit(TypeBoolean type) {
		model0(type, "false");
	}

	@Override
	public void visit(TypeByte type) {
		model0(type, "0");
	}

	@Override
	public void visit(TypeInt type) {
		model0(type, "0");
	}

	@Override
	public void visit(TypeShort type) {
		model0(type, "0");
	}

	@Override
	public void visit(TypeLong type) {
		model0(type, "0L");
	}

	@Override
	public void visit(TypeFloat type) {
		model0(type, "0.f");
	}

	@Override
	public void visit(TypeDouble type) {
		model0(type, "0.0");
	}

	@Override
	public void visit(TypeBinary type) {
		if (Main.isMakingZdb)
			model0(type, "new byte[]");
		else
			model1(type);
	}

	@Override
	public void visit(TypeString type) {
		model0(type, "\"\"");
	}

	private void model1(Type type) {
		String typename = TypeName.getName(type);
		ps.println(prefix + typename + " " + varname + " = new " + typename + "();");
	}

	@Override
	public void visit(TypeList type) {
		this.model1(type);
	}

	@Override
	public void visit(TypeVector type) {
		this.model1(type);
	}

	@Override
	public void visit(TypeSet type) {
		this.model1(type);
	}

	@Override
	public void visit(TypeMap type) {
		this.model1(type);
	}

	@Override
	public void visit(Bean type) {
		this.model1(type);
	}

	@Override
	public void visit(Cbean type) {
		this.model1(type);
	}

	@Override
	public void visit(Xbean type) {
		String typename = TypeName.getName(type);
		if (useXbeanParent_VarName != null)
			ps.println(prefix + typename + " " + varname + " = new " + type.getName() + "(" + xbeanParent + ", "
					+ StringUtils.quote(useXbeanParent_VarName) + ");");
		else
			ps.println(prefix + typename + " " + varname + " = new " + typename + "();");
	}

	@Override
	public void visit(TypeAny type) {
		ps.println(prefix + type.getTypeName() + " " + varname + " = null;");
	}
}
