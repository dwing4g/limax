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

class Define implements Visitor {

	public interface GetString {
		String getString();
	}

	public enum Permission implements GetString {
		Public {
			@Override
			public String getString() {
				return "public ";
			}
		},
		Private {
			@Override
			public String getString() {
				return "private ";
			}
		},
		None {
			@Override
			public String getString() {
				return "";
			}
		},
	}

	private final String varname;
	private final PrintStream ps;
	private final String prefix;
	private final boolean initial;
	private final Permission permission;

	public static void make(Protocol protocol, PrintStream ps, String prefix) {
		make(ps, protocol.getImplementBean().getVariables(), prefix, Permission.Public, false);
	}

	public static void make(Bean bean, PrintStream ps, String prefix) {
		make(ps, bean.getVariables(), prefix, Permission.Public, false);
	}

	public static void make(Cbean bean, PrintStream ps, String prefix) {
		make(ps, bean.getVariables(), prefix, Permission.Public, false);
	}

	public static void make(Xbean bean, PrintStream ps, String prefix) {
		make(ps, bean.getVariables(), prefix, Permission.Public, false);
	}

	public static void make(Bind bind, PrintStream ps, String prefix) {
		make(ps, bind.getVariables(), prefix, Permission.Public, false);
	}

	public static void make(Control control, PrintStream ps, String prefix) {
		make(ps, control.getImplementBean().getVariables(), prefix, Permission.Public, false);
	}

	public static void make(View view, PrintStream ps, String prefix) {
		make(ps, view.getVariables(), prefix, Permission.Private, true);
		for (Bind bind : view.getBinds()) {
			if (bind.isFullBind())
				Define.make(bind.getValueType(), bind.getName(), ps, prefix, Permission.Private, true);
			else {
				final String type = view.getName() + ".__" + bind.getName();
				ps.println(prefix + Permission.Private.getString() + type + " " + bind.getName() + " = new " + type
						+ "();");
			}
		}
	}

	private static void make(PrintStream ps, Collection<Variable> vars, String prefix, Permission permission,
			boolean initial) {
		if (vars.isEmpty())
			return;
		for (final Variable var : vars)
			var.getType().accept(new Define(var.getName(), ps, prefix, permission, initial));
	}

	public static void make(Type type, String name, PrintStream ps, String prefix, Permission permission,
			boolean initial) {
		type.accept(new Define(name, ps, prefix, permission, initial));
	}

	private Define(String varname, PrintStream ps, String prefix, Permission permisson, boolean initial) {
		this.varname = varname;
		this.ps = ps;
		this.prefix = prefix;
		this.initial = initial;
		this.permission = permisson;
	}

	private void model0(Type type, String init) {
		ps.print(prefix + permission.getString() + TypeName.getName(type) + " " + varname);
		if (initial)
			ps.println(" = " + init + ";");
		else
			ps.println(";");
	}

	private void model1(Type type) {
		String typename = TypeName.getName(type);
		ps.print(prefix + permission.getString() + typename + " " + varname);
		if (initial)
			ps.println(" = new " + typename + "();");
		else
			ps.println(";");
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
		model1(type);
	}

	@Override
	public void visit(TypeString type) {
		model0(type, "\"\"");
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
		this.model1(type);
	}

	@Override
	public void visit(TypeAny type) {
		throw new UnsupportedOperationException();
	}
}
