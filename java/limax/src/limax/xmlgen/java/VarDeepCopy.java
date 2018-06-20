package limax.xmlgen.java;

import java.io.PrintStream;

import limax.util.StringUtils;
import limax.xmlgen.Bean;
import limax.xmlgen.Cbean;
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
import limax.xmlgen.Variable;
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;

class VarDeepCopy implements Visitor {

	static void make(Variable var, PrintStream ps, String prefix) {
		var.getType().accept(new VarDeepCopy(var.getName(), ps, prefix));
	}

	private static String getCopy(Type type, String fullvarname, String parentVarname) {
		if (type instanceof TypeBinary) {
			return "java.util.Arrays.copyOf(" + fullvarname + ", " + fullvarname + ".length)";
		} else if (type instanceof Xbean) {
			return "new " + type.getName() + "(" + fullvarname + ", this, " + StringUtils.quote(parentVarname) + ")";
		} else {
			return fullvarname;
		}
	}

	private String varname;
	private String this_varname;
	private PrintStream ps;
	private String prefix;

	private VarDeepCopy(String varname, PrintStream ps, String prefix) {
		this.varname = varname;
		this.ps = ps;
		this.prefix = prefix;
		this.this_varname = "this." + varname;
	}

	@Override
	public void visit(Cbean type) {
		simple();
	}

	@Override
	public void visit(Xbean type) {
		ps.println(prefix + this_varname + " = new " + type.getName() + "(_o_." + varname + ", this, "
				+ StringUtils.quote(varname) + ");");
	}

	@Override
	public void visit(TypeString type) {
		simple();
	}

	@Override
	public void visit(TypeBinary type) {
		ps.println(prefix + this_varname + " = " + getCopy(type, "_o_." + varname, varname) + ";");

	}

	private void simple() {
		ps.println(prefix + this_varname + " = _o_." + varname + ";");
	}

	@Override
	public void visit(TypeBoolean type) {
		simple();
	}

	@Override
	public void visit(TypeByte type) {
		simple();
	}

	@Override
	public void visit(TypeShort type) {
		simple();
	}

	@Override
	public void visit(TypeInt type) {
		simple();
	}

	@Override
	public void visit(TypeLong type) {
		simple();
	}

	@Override
	public void visit(TypeFloat type) {
		simple();
	}

	@Override
	public void visit(TypeDouble type) {
		simple();
	}

	private void collection(Type type, Type value) {
		ps.println(prefix + this_varname + " = new " + TypeName.getName(type) + "();");
		if (value.isConstType())
			ps.println(prefix + this_varname + ".addAll(_o_." + varname + ");");
		else
			ps.println(prefix + "_o_." + varname + ".forEach(_v_ -> " + this_varname + ".add("
					+ getCopy(value, "_v_", varname) + "));");
	}

	@Override
	public void visit(TypeList type) {
		collection(type, type.getValueType());
	}

	@Override
	public void visit(TypeVector type) {
		collection(type, type.getValueType());
	}

	@Override
	public void visit(TypeSet type) {
		collection(type, type.getValueType());
	}

	@Override
	public void visit(TypeMap type) {
		ps.println(prefix + this_varname + " = new " + TypeName.getName(type) + "();");
		Type key = type.getKeyType();
		Type value = type.getValueType();
		String keycopy = key.isConstType() ? "_k_" : getCopy(key, "_k_", varname);
		String valuecopy = value.isConstType() ? "_v_" : getCopy(value, "_v_", varname);
		ps.println(prefix + "_o_." + varname + ".forEach((_k_, _v_) -> " + this_varname + ".put(" + keycopy + ", "
				+ valuecopy + "));");
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
