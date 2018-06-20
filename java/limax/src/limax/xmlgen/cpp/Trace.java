package limax.xmlgen.cpp;

import java.io.PrintStream;
import java.util.Collection;

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
import limax.xmlgen.View;
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;

class Trace implements Visitor {
	private String varname;
	private PrintStream ps;
	private String prefix;

	private static void make(Collection<Variable> vars, PrintStream ps, String prefix, String proto) {
		ps.println(prefix + "std::ostream & trace(std::ostream & _os_) const");
		ps.println(prefix + "{");

		if (proto != null) {
			ps.println(prefix + "    _os_ << \"" + proto + ":{\";");
		}

		for (Variable var : vars)
			var.getType().accept(new Trace(var.getName(), ps, prefix + "	"));

		if (proto != null) {
			ps.println(prefix + "    _os_ << \"}\";");
		}

		ps.println(prefix + "	return _os_;");
		ps.println(prefix + "}");
		ps.println();
	}

	public static void make(Bean bean, PrintStream ps, String prefix, String proto) {
		make(bean.getVariables(), ps, prefix, proto);
	}

	public static void make(Cbean bean, PrintStream ps, String prefix, String proto) {
		make(bean.getVariables(), ps, prefix, proto);
	}

	public static void make(Xbean bean, PrintStream ps, String prefix, String proto) {
		make(bean.getVariables(), ps, prefix, proto);
	}

	public static void make(View view, PrintStream ps, String prefix, String prot) {
		make(view.getVariables(), ps, prefix, prot);
	}

	public Trace(String varname, PrintStream ps, String prefix) {
		this.varname = varname;
		this.ps = ps;
		this.prefix = prefix;
	}

	@Override
	public void visit(Bean bean) {
		ps.println(prefix + "_os_ << " + varname + " << \",\";");
	}

	@Override
	public void visit(TypeByte type) {
		ps.println(prefix + "_os_ << (int)" + varname + " << \",\";");
	}

	@Override
	public void visit(TypeFloat type) {
		ps.println(prefix + "_os_ << " + varname + " << \",\";");
	}

	@Override
	public void visit(TypeDouble type) {
		ps.println(prefix + "_os_ << " + varname + " << \",\";");
	}

	@Override
	public void visit(TypeInt type) {
		ps.println(prefix + "_os_ << " + varname + " << \",\";");
	}

	@Override
	public void visit(TypeShort type) {
		ps.println(prefix + "_os_ << " + varname + " << \",\";");
	}

	private void traceValueType(Type type, Type valueType) {
		ps.println(prefix + "_os_ << \"[\";");
		String typeName = TypeName.getName(type);
		ps.println(prefix + "for (" + typeName + "::const_iterator _i_ = " + varname + ".begin(), _e_ = " + varname
				+ ".end(); _i_ != _e_; ++_i_)");
		ps.println(prefix + "{");
		valueType.accept(new Trace("(*_i_)", ps, prefix + "	"));
		ps.println(prefix + "}");
		ps.println(prefix + "_os_ << \"]\";");
	}

	@Override
	public void visit(TypeList type) {
		this.traceValueType(type, type.getValueType());
	}

	@Override
	public void visit(TypeLong type) {
		ps.println(prefix + "_os_ << " + varname + " << \",\";");
	}

	@Override
	public void visit(TypeMap type) {
		ps.println(prefix + "_os_ << \"{\";");
		String typeName = TypeName.getName(type);
		ps.println(prefix + "for (" + typeName + "::const_iterator _i_ = " + varname + ".begin(), _e_ = " + varname
				+ ".end(); _i_ != _e_; ++_i_)");
		ps.println(prefix + "{");
		ps.println(prefix + "	_os_ << \"(\";");
		type.getKeyType().accept(new Trace("_i_->first", ps, prefix + "	"));
		type.getValueType().accept(new Trace("_i_->second", ps, prefix + "	"));
		ps.println(prefix + "	_os_ << \")\";");
		ps.println(prefix + "}");
		ps.println(prefix + "_os_ << \"}\";");
	}

	@Override
	public void visit(TypeBinary type) {
		ps.println(prefix + "_os_ << \"" + varname + ".size=\" << " + varname + ".size() << \",\";");
	}

	@Override
	public void visit(TypeSet type) {
		this.traceValueType(type, type.getValueType());
	}

	@Override
	public void visit(TypeString type) {
		ps.println(prefix + "_os_ << \"" + varname + ".size=\" << " + varname + ".size() << \",\";");
	}

	@Override
	public void visit(TypeVector type) {
		this.traceValueType(type, type.getValueType());
	}

	@Override
	public void visit(Cbean type) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(Xbean type) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(TypeBoolean type) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visit(TypeAny type) {
		throw new UnsupportedOperationException();
	}
}
