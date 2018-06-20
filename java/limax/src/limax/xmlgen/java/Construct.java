package limax.xmlgen.java;

import java.io.PrintStream;
import java.util.Collection;

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
import limax.xmlgen.Variable;
import limax.xmlgen.View;
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;
import limax.xmlgen.Xbean.DynamicVariable;

public class Construct implements Visitor {
	private final PrintStream ps;
	private final String variable;
	private final String varname;
	private final String prefix;

	public static void make(View view, String name, PrintStream ps, String prefix, boolean forserver,
			boolean abstractclass) {
		if (forserver) {
			switch (view.getLifecycle()) {
			case global:
				ps.println(prefix + "_" + name + "(limax.provider.GlobalView.CreateParameter param) {");
				ps.println(prefix + "	super(param, _prefix_, _collectors_, _varnames_);");
				break;
			case session:
				ps.println(prefix + "_" + name + "(limax.provider.SessionView.CreateParameter param) {");
				ps.println(prefix + "	super(param, _prefix_, _collectors_);");
				break;
			case temporary:
				ps.println(prefix + "_" + name + "(limax.provider.TemporaryView.CreateParameter param) {");
				ps.println(prefix + "	super(param, _prefix_, _collectors_, _subscribes_, _subscribe_collectors_);");
				break;
			}
			if (!view.getBinds().isEmpty())
				ps.println(prefix + "	this.resource = param.getResource();");
		} else {
			ps.println(prefix + (abstractclass ? "_" : "") + name + "(ViewContext vc) {");
			ps.println(prefix + "	super(vc);");
			ps.println(prefix + "	__pvid__ = vc.getProviderId();");
		}

		if (!forserver) {
			for (Variable var : view.getVariables())
				var.getType().accept(new Construct(var.getName(), ps, prefix + "	"));
			for (Bind bind : view.getBinds()) {
				if (bind.isFullBind()) {
					bind.getValueType().accept(new Construct(bind.getName(), ps, prefix + "	"));
				} else {
					ps.println(prefix + "	this." + bind.getName() + " = new " + bind.getName() + "();");
				}
			}
		}
		ps.println(prefix + "}");
		ps.println();
	}

	private static void make(Collection<Variable> variables, String name, PrintStream ps, String prefix) {
		ps.println(prefix + "public " + name + "() {");
		for (Variable var : variables)
			make(var, ps, prefix + "	");
		ps.println(prefix + "}");
		ps.println();
	}

	public static void make(Bean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), bean.getLastName(), ps, prefix);
	}

	public static void make(Cbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), bean.getLastName(), ps, prefix);
	}

	public static void make(Xbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), bean.getLastName(), ps, prefix);
	}

	public static void make(Bind bind, PrintStream ps, String prefix) {
		make(bind.getVariables(), bind.getName(), ps, prefix);
	}

	public static void make(Variable var, PrintStream ps, String prefix) {
		var.getType().accept(new Construct(var.getName(), ps, prefix));
	}

	public static void make(DynamicVariable var, PrintStream ps, String prefix) {
		var.getType().accept(new Construct(var.getName(), ps, prefix));
	}

	public Construct(String varname, PrintStream ps, String prefix) {
		this.variable = varname;
		this.varname = varname;
		this.ps = ps;
		this.prefix = prefix;
	}

	private void initial() {
	}

	private void newVariable(Type type) {
		ps.println(prefix + variable + " = new " + TypeName.getName(type) + "();");
	}

	@Override
	public void visit(Bean type) {
		newVariable(type);
	}

	@Override
	public void visit(TypeFloat type) {
		initial();
	}

	@Override
	public void visit(TypeDouble type) {
		initial();
	}

	@Override
	public void visit(TypeBoolean type) {
		initial();
	}

	@Override
	public void visit(TypeByte type) {
		initial();
	}

	@Override
	public void visit(TypeInt type) {
		initial();
	}

	@Override
	public void visit(TypeShort type) {
		initial();
	}

	@Override
	public void visit(TypeLong type) {
		initial();
	}

	@Override
	public void visit(TypeBinary type) {
		if (Main.isMakingZdb)
			ps.println(prefix + variable + " = new byte[0];");
		else
			newVariable(type);
	}

	@Override
	public void visit(TypeString type) {
		ps.println(prefix + variable + " = \"\";");
	}

	@Override
	public void visit(TypeList type) {
		newVariable(type);
	}

	@Override
	public void visit(TypeVector type) {
		newVariable(type);
	}

	@Override
	public void visit(TypeSet type) {
		newVariable(type);
	}

	@Override
	public void visit(TypeMap type) {
		newVariable(type);
	}

	@Override
	public void visit(Cbean type) {
		if (Main.isMakingZdb) {
			ps.println(prefix + variable + " = new " + TypeName.getName(type) + "();");
		} else {
			newVariable(type);
		}
	}

	@Override
	public void visit(Xbean type) {
		if (Main.isMakingZdb)
			ps.println(prefix + variable + " = new " + type.getName() + "(this, " + StringUtils.quote(varname) + ");");
		else
			newVariable(type);
	}

	@Override
	public void visit(TypeAny type) {
		ps.println(prefix + variable + " = null;");
	}
}
