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

class Marshal implements Visitor {
	private PrintStream ps;
	private String prefix;
	private String varname;

	private static void make(Collection<Variable> vars, PrintStream ps, String prefix, boolean needoverride) {
		final String declare = "public" + (needoverride ? " override" : "");
		ps.println(prefix + declare + " OctetsStream marshal(OctetsStream _os_)");
		ps.println(prefix + "{");
		for (Variable var : vars)
			printMarshalElement(var, ps, prefix + "	", "this." + var.getName());
		ps.println(prefix + "	return _os_;");
		ps.println(prefix + "}");
	}

	public static void printMarshalElement(Variable var, PrintStream ps, String prefix, String varname) {
		var.getType().accept(new Marshal(ps, prefix, varname));
	}

	public static void printMarshalElement(Bind bind, PrintStream ps, String prefix, String varname) {
		if (bind.isFullBind())
			bind.getValueType().accept(new Marshal(ps, prefix, varname));
		else
			ps.println(prefix + varname + ".marshal( _os_);");
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

	public static void make(Bind bind, PrintStream ps, String prefix) {
		make(bind.getVariables(), ps, prefix, false);
	}

	public static void make(View view, PrintStream ps, String prefix) {
		ps.println(prefix + "public OctetsStream marshal(OctetsStream _os_)");
		ps.println(prefix + "{");
		for (Variable var : view.getVariables())
			printMarshalElement(var, ps, prefix + "	", "this." + var.getName());
		for (Bind bind : view.getBinds())
			printMarshalElement(bind, ps, prefix + "	", "this." + bind.getName());
		ps.println(prefix + "	return _os_;");
		ps.println(prefix + "}");
	}

	private Marshal(PrintStream ps, String prefix, String varname) {
		this.ps = ps;
		this.prefix = prefix;
		this.varname = varname;
	}

	@Override
	public void visit(TypeByte type) {
		ps.println(prefix + "_os_.marshal(" + varname + ");");
	}

	@Override
	public void visit(TypeInt type) {
		ps.println(prefix + "_os_.marshal(" + varname + ");");
	}

	@Override
	public void visit(TypeShort type) {
		ps.println(prefix + "_os_.marshal(" + varname + ");");
	}

	@Override
	public void visit(TypeFloat type) {
		ps.println(prefix + "_os_.marshal(" + varname + ");");
	}

	@Override
	public void visit(TypeDouble type) {
		ps.println(prefix + "_os_.marshal(" + varname + ");");
	}

	@Override
	public void visit(TypeLong type) {
		ps.println(prefix + "_os_.marshal(" + varname + ");");
	}

	private void marshalContainer(Type valuetype) {
		ps.println(prefix + "_os_.marshal_size(" + varname + ".Count);");
		ps.println(prefix + "foreach(var __v__ in " + varname + ")");
		valuetype.accept(new Marshal(ps, prefix + "	", "__v__"));
	}

	private void marshalContainer(Type keytype, Type valuetype) {
		ps.println(prefix + "_os_.marshal_size(" + varname + ".Count);");
		ps.println(prefix + "foreach(var __v__ in " + varname + ")");
		ps.println(prefix + "{");
		keytype.accept(new Marshal(ps, prefix + "	", "__v__.Key"));
		valuetype.accept(new Marshal(ps, prefix + "	", "__v__.Value"));
		ps.println(prefix + "}");
	}

	@Override
	public void visit(TypeList type) {
		marshalContainer(type.getValueType());
	}

	@Override
	public void visit(TypeMap type) {
		marshalContainer(type.getKeyType(), type.getValueType());
	}

	@Override
	public void visit(TypeBinary type) {
		ps.println(prefix + "_os_.marshal(" + varname + ");");
	}

	@Override
	public void visit(TypeSet type) {
		marshalContainer(type.getValueType());
	}

	@Override
	public void visit(TypeString type) {
		ps.println(prefix + "_os_.marshal(" + varname + ");");
	}

	@Override
	public void visit(TypeVector type) {
		marshalContainer(type.getValueType());
	}

	@Override
	public void visit(Bean bean) {
		ps.println(prefix + "_os_.marshal(" + varname + ");");
	}

	@Override
	public void visit(Cbean type) {
		ps.println(prefix + "_os_.marshal(" + varname + ");");
	}

	@Override
	public void visit(Xbean type) {
		ps.println(prefix + "_os_.marshal(" + varname + ");");
	}

	@Override
	public void visit(TypeBoolean type) {
		ps.println(prefix + "_os_.marshal(" + varname + ");");
	}

	@Override
	public void visit(TypeAny type) {
		throw new UnsupportedOperationException();
	}

}
