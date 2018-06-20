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

class Ostream implements Visitor {
	private Variable _var;
	private String _text;
	private PrintStream _ps;
	private String _prefix;

	public String getText() {
		return _text;
	}

	public Ostream(Variable var, PrintStream ps, String prefix) {
		_var = var;
		_ps = ps;
		_prefix = prefix;
	}

	private static void make(String name, Collection<Variable> vars, PrintStream ps, String prefix, String prot) {
		ps.println(prefix + "friend std::ostream& operator<<(std::ostream& _s_, const " + name + " & _obj_)");
		ps.println(prefix + "{");
		if (prot != null)
			ps.println(prefix + "	_s_ << \"" + prot + ": {\";");
		for (Variable var : vars)
			var.getType().accept(new Ostream(var, ps, prefix + "	"));
		if (prot != null)
			ps.println(prefix + "	_s_ << \" }\";");
		ps.println(prefix + "	return _s_;");
		ps.println(prefix + "}");
		ps.println();
	}

	public static void make(Bean bean, PrintStream ps, String prefix, String prot) {
		make(bean.getName(), bean.getVariables(), ps, prefix, prot);
	}

	public static void make(Cbean bean, PrintStream ps, String prefix, String prot) {
		make(bean.getName(), bean.getVariables(), ps, prefix, prot);
	}

	public static void make(Xbean bean, PrintStream ps, String prefix, String prot) {
		make(bean.getName(), bean.getVariables(), ps, prefix, prot);
	}

	public static void make(View view, PrintStream ps, String prefix, String prot) {
		make(view.getLastName(), view.getVariables(), ps, prefix, prot);
	}

	@Override
	public void visit(Bean type) {
		basicVarVisit(type);
	}

	private void basicVarVisit(limax.xmlgen.Type type) {
		_text = _prefix + "_s_ << \"" + _var.getName() + ": {\" << " + skipOctets(type, "_obj_." + _var.getName())
				+ " << \"}, \";";
		_ps.println(_text);
	}

	private void complexVarVisit(Type type, Type valueType) {
		String otype = skipOctets(valueType, "*_i_");
		_ps.println(_prefix + "	_s_ << \"" + _var.getName() + ": {\";");
		_text = "	for (" + TypeName.getName(type) + "::const_iterator _i_ = _obj_." + _var.getName()
				+ ".begin(); _i_ != _obj_." + _var.getName() + ".end(); ++_i_){";
		_ps.println(_prefix + _text);
		_text = "		_s_ << \"(\" << " + otype + "<< \"), \";}";
		_ps.println(_prefix + _text);
		_ps.println(_prefix + "	_s_ << \"}\";");
	}

	@Override
	public void visit(TypeByte type) {
		basicVarVisit(type);
	}

	@Override
	public void visit(TypeInt type) {
		basicVarVisit(type);
	}

	@Override
	public void visit(TypeLong type) {
		basicVarVisit(type);
	}

	@Override
	public void visit(TypeBinary type) {
		basicVarVisit(type);
	}

	private String skipOctets(Type t, String out) {
		if (t instanceof TypeBinary || t instanceof TypeString) {
			return "\"[Octets]\"";
		} else {
			return out;
		}
	}

	@Override
	public void visit(TypeString type) {
		basicVarVisit(type);
	}

	@Override
	public void visit(TypeList type) {
		complexVarVisit(type, type.getValueType());
	}

	@Override
	public void visit(TypeVector type) {
		complexVarVisit(type, type.getValueType());
	}

	@Override
	public void visit(TypeSet type) {
		complexVarVisit(type, type.getValueType());
	}

	@Override
	public void visit(TypeMap type) {
		String okey = skipOctets(type.getKeyType(), "_i_->first");
		String ovalue = skipOctets(type.getValueType(), "_i_->second");

		_ps.println(_prefix + "		_s_ << \"" + _var.getName() + ": {\";");
		_text = "		for (" + TypeName.getName(type) + "::const_iterator _i_ = _obj_." + _var.getName()
				+ ".begin(); _i_ != _obj_." + _var.getName() + ".end(); ++_i_){";
		_ps.println(_prefix + _text);
		_text = "			_s_ << \"(\" << " + okey + " << \"=\" << " + ovalue + " <<\"), \";}";
		_ps.println(_prefix + _text);
		_ps.println(_prefix + "		_s_ << \"}\";");
	}

	@Override
	public void visit(TypeFloat type) {
		basicVarVisit(type);
	}

	@Override
	public void visit(TypeDouble type) {
		basicVarVisit(type);
	}

	@Override
	public void visit(TypeShort type) {
		basicVarVisit(type);
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
