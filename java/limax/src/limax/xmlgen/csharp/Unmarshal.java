package limax.xmlgen.csharp;

import java.io.PrintStream;
import java.util.Collection;

import limax.xmlgen.Bean;
import limax.xmlgen.Bind;
import limax.xmlgen.Cbean;
import limax.xmlgen.Control;
import limax.xmlgen.Protocol;
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
import limax.xmlgen.View;
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;

class Unmarshal implements Visitor {
	private String varname;
	private PrintStream ps;
	private String prefix;

	private static void make(Collection<Variable> vars, PrintStream ps, String prefix, boolean needoverride) {
		final String declare = "public" + (needoverride ? " override" : "");
		ps.println(prefix + declare + " OctetsStream unmarshal(OctetsStream _os_)");
		ps.println(prefix + "{");
		for (Variable var : vars)
			var.getType().accept(new Unmarshal("this." + var.getName(), ps, prefix + "	"));
		ps.println(prefix + "	return _os_;");
		ps.println(prefix + "}");
	}

	public static void make(Protocol protocol, PrintStream ps, String prefix) {
		make(protocol.getImplementBean().getVariables(), ps, prefix, true);
	}

	public static void make(Control control, PrintStream ps, String prefix) {
		make(control.getImplementBean().getVariables(), ps, prefix, true);
	}

	public static void make(Bean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix, false);
	}

	public static void make(Cbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix, false);
	}

	public static void make(Xbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix, false);
	}

	public static void make(View view, PrintStream ps, String prefix) {
		ps.println(prefix + "public OctetsStream unmarshal(OctetsStream _os_)");
		ps.println(prefix + "{");
		for (Variable var : view.getVariables())
			var.getType().accept(new Unmarshal("this." + var.getName(), ps, prefix + "	"));
		for (Bind bind : view.getBinds()) {
			if (bind.isFullBind())
				bind.getValueType().accept(new Unmarshal("this." + bind.getName(), ps, prefix + "	"));
			else
				ps.println(prefix + "	" + bind.getName() + ".unmarshal(_os_);");
		}
		ps.println(prefix + "	return _os_;");
		ps.println(prefix + "}");
	}

	public static void make(Bind bind, PrintStream ps, String prefix) {
		make(bind.getVariables(), ps, prefix, false);
	}

	public static void make(Type type, String varname, PrintStream ps, String prefix) {
		type.accept(new Unmarshal(varname, ps, prefix));
	}

	private Unmarshal(String varname, PrintStream ps, String prefix) {
		this.varname = varname;
		this.ps = ps;
		this.prefix = prefix;
	}

	@Override
	public void visit(Bean bean) {
		ps.println(prefix + "_os_.unmarshal(" + varname + ");");
	}

	@Override
	public void visit(TypeByte type) {
		ps.println(prefix + varname + " = _os_.unmarshal_byte();");
	}

	@Override
	public void visit(TypeFloat type) {
		ps.println(prefix + varname + " = _os_.unmarshal_float();");
	}

	@Override
	public void visit(TypeDouble type) {
		ps.println(prefix + varname + " = _os_.unmarshal_double();");
	}

	@Override
	public void visit(TypeInt type) {
		ps.println(prefix + varname + " = _os_.unmarshal_int();");
	}

	@Override
	public void visit(TypeShort type) {
		ps.println(prefix + varname + " = _os_.unmarshal_short();");
	}

	@Override
	public void visit(TypeLong type) {
		ps.println(prefix + varname + " = _os_.unmarshal_long();");
	}

	@Override
	public void visit(TypeBinary type) {
		ps.println(prefix + varname + " = _os_.unmarshal_Octets();");
	}

	@Override
	public void visit(TypeString type) {
		ps.println(prefix + varname + " = _os_.unmarshal_string();");
	}

	private void unmarshalContainer(Type valuetype) {
		unmarshalContainer(valuetype, "Add");
	}

	private void unmarshalContainer(Type valuetype, String methodname) {
		ps.println(prefix + "for(int _i_ = _os_.unmarshal_size(); _i_ > 0; --_i_)");
		ps.println(prefix + "{");
		ConstructWithUnmarshal.make(valuetype, "_v_", ps, prefix + "	");
		ps.println(prefix + "	" + varname + "." + methodname + "(_v_);");
		ps.println(prefix + "}");
	}

	private void unmarshalContainer(Type keytype, Type valuetype) {
		ps.println(prefix + "for(int _i_ = _os_.unmarshal_size(); _i_ > 0; --_i_)");
		ps.println(prefix + "{");
		ConstructWithUnmarshal.make(keytype, "_k_", ps, prefix + "	");
		ConstructWithUnmarshal.make(valuetype, "_v_", ps, prefix + "	");
		ps.println(prefix + "	" + varname + "[_k_] = _v_;");
		ps.println(prefix + "}");
	}

	@Override
	public void visit(TypeList type) {
		unmarshalContainer(type.getValueType(), "AddLast");
	}

	@Override
	public void visit(TypeVector type) {
		unmarshalContainer(type.getValueType());
	}

	@Override
	public void visit(TypeSet type) {
		unmarshalContainer(type.getValueType());
	}

	@Override
	public void visit(TypeMap type) {
		unmarshalContainer(type.getKeyType(), type.getValueType());
	}

	@Override
	public void visit(Cbean type) {
		ps.println(prefix + "_os_.unmarshal(" + varname + ");");
	}

	@Override
	public void visit(Xbean type) {
		ps.println(prefix + "_os_.unmarshal(" + varname + ");");
	}

	@Override
	public void visit(TypeBoolean type) {
		ps.println(prefix + varname + " = _os_.unmarshal_bool();");
	}

	@Override
	public void visit(TypeAny type) {
		throw new UnsupportedOperationException();
	}
}
