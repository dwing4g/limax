package limax.xmlgen.java;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import limax.xmlgen.Bean;
import limax.xmlgen.Bind;
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
import limax.xmlgen.View;
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;
import limax.xmlgen.Xbean.DynamicVariable;

public class Marshal implements Visitor {
	private final PrintStream ps;
	private final String prefix;
	private final String varname;

	public static void make(View view, PrintStream ps, String prefix) {
		ps.println(prefix + "@Override");
		ps.println(prefix + "protected final limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {");
		printMarshalViewElement(view, ps, prefix + "	");
		ps.println(prefix + "	return _os_;");
		ps.println(prefix + "}");
		ps.println();
	}

	public static void printMarshalViewElement(View view, PrintStream ps, String prefix) {
		view.getVariables().forEach(var -> make(var, ps, prefix));
		view.getBinds().forEach(bind -> make(bind, ps, prefix));
	}

	public static void make(Bind bind, PrintStream ps, String prefix) {
		if (bind.isFullBind())
			bind.getValueType().accept(new Marshal("this." + bind.getName(), ps, prefix));
		else
			ps.println(prefix + "_os_.marshal(this." + bind.getName() + ");");
	}

	public static void make(Collection<Variable> variables, PrintStream ps, String prefix) {
		ps.println(prefix + "@Override");
		ps.println(prefix + "public limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {");
		variables.forEach(var -> var.getType().accept(new Marshal("this." + var.getName(), ps, prefix + "	")));
		ps.println(prefix + "	return _os_;");
		ps.println(prefix + "}");
		ps.println();
	}

	public static List<Runnable> make(Collection<Variable> svars, Collection<DynamicVariable> dvars, PrintStream ps,
			String prefix) {
		List<Runnable> delayed = new ArrayList<>();
		svars.forEach(var -> Marshal.make(var, ps, prefix + "	"));
		if (dvars != null) {
			ps.println(
					prefix + "	_os_.marshal_size(" + dvars.stream().filter(DynamicVariable::isMaster).count() + ");");
			dvars.stream().filter(DynamicVariable::isMaster).forEach(var -> {
				int serial = var.getSerial();
				ps.println(prefix + "	_os_.marshal_size(" + serial + ").marshal(marshal" + serial + "());");
				delayed.add(() -> {
					ps.println(prefix + "private OctetsStream marshal" + serial + "() {");
					ps.println(prefix + "	OctetsStream _os_ = new OctetsStream();");
					Marshal.make(var.getType(), "this." + var.getName(), ps, prefix + "	");
					ps.println(prefix + "	return _os_;");
					ps.println(prefix + "}");
					ps.println();
				});
			});
		}
		ps.println(prefix + "	return _os_;");
		return delayed;
	}

	public static void make(Bean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Cbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Xbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Variable var, PrintStream ps, String prefix) {
		var.getType().accept(new Marshal("this." + var.getName(), ps, prefix));
	}

	public static void make(Type type, String varname, PrintStream ps, String prefix) {
		type.accept(new Marshal(varname, ps, prefix));
	}

	private Marshal(String varname, PrintStream ps, String prefix) {
		this.varname = varname;
		this.ps = ps;
		this.prefix = prefix;
	}

	private void model0() {
		ps.println(prefix + "_os_.marshal(" + varname + ");");
	}

	@Override
	public void visit(Bean bean) {
		model0();
	}

	@Override
	public void visit(TypeByte type) {
		model0();
	}

	@Override
	public void visit(TypeFloat type) {
		model0();
	}

	@Override
	public void visit(TypeDouble type) {
		model0();
	}

	@Override
	public void visit(TypeInt type) {
		model0();
	}

	@Override
	public void visit(TypeShort type) {
		model0();
	}

	@Override
	public void visit(TypeLong type) {
		model0();
	}

	@Override
	public void visit(TypeBinary type) {
		model0();
	}

	@Override
	public void visit(TypeString type) {
		ps.println(prefix + "_os_.marshal(" + varname + ");");
	}

	private void marshalContainer(Type keyType, Type valueType) {
		String key = TypeName.getBoxingName(keyType);
		String value = TypeName.getBoxingName(valueType);
		String KV = "<" + key + ", " + value + ">";
		ps.println(prefix + "_os_.marshal_size(" + varname + ".size());");
		ps.println(prefix + "for (java.util.Map.Entry" + KV + " _e_ : " + varname + ".entrySet()) {");
		keyType.accept(new Marshal("_e_.getKey()", ps, prefix + "	"));
		valueType.accept(new Marshal("_e_.getValue()", ps, prefix + "	"));
		ps.println(prefix + "}");
	}

	@Override
	public void visit(TypeMap type) {
		marshalContainer(type.getKeyType(), type.getValueType());
	}

	private void marshalContainer(Type valueType) {
		ps.println(prefix + "_os_.marshal_size(" + varname + ".size());");
		ps.println(prefix + "for (" + TypeName.getBoxingName(valueType) + " _v_ : " + varname + ") {");
		valueType.accept(new Marshal("_v_", ps, prefix + "	"));
		ps.println(prefix + "}");
	}

	@Override
	public void visit(TypeList type) {
		this.marshalContainer(type.getValueType());
	}

	@Override
	public void visit(TypeSet type) {
		this.marshalContainer(type.getValueType());
	}

	@Override
	public void visit(TypeVector type) {
		this.marshalContainer(type.getValueType());
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
		model0();
	}

	@Override
	public void visit(TypeAny type) {
		throw new UnsupportedOperationException();
	}

}
