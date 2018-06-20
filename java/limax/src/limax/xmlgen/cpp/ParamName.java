package limax.xmlgen.cpp;

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
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;

class ParamName implements Visitor {
	private String name;

	public static String getName(Type type) {
		ParamName visitor = new ParamName();
		type.accept(visitor);
		return visitor.name;
	}

	public static String getParamList(Collection<Variable> variables) {
		StringBuilder params = new StringBuilder();
		boolean first = true;
		for (Variable var : variables) {
			if (first)
				first = false;
			else
				params.append(", ");
			params.append(ParamName.getName(var.getType())).append(" _").append(var.getName()).append("_");
		}
		return params.toString();
	}

	public static String getCopyConstructList(Collection<Variable> variables) {
		StringBuilder inits = new StringBuilder();
		for (Variable var : variables) {
			inits.append(", ");
			inits.append(var.getName()).append("(").append("_src_." + var.getName()).append(")");
		}
		return inits.toString();
	}

	public static String getInitConstructList(Collection<Variable> variables) {
		StringBuilder inits = new StringBuilder();
		boolean first = true;
		for (Variable var : variables) {
			if (first)
				first = false;
			else
				inits.append(", ");
			inits.append(var.getName()).append("(").append("_" + var.getName() + "_").append(")");
			first = false;
		}
		return inits.toString();
	}

	@Override
	public void visit(Bean type) {
		name = "const " + TypeName.getName(type) + " &";
	}

	@Override
	public void visit(TypeByte type) {
		name = TypeName.getName(type);
	}

	@Override
	public void visit(TypeInt type) {
		name = TypeName.getName(type);
	}

	@Override
	public void visit(TypeShort type) {
		name = TypeName.getName(type);
	}

	@Override
	public void visit(TypeLong type) {
		name = TypeName.getName(type);
	}

	@Override
	public void visit(TypeBinary type) {
		name = "const " + TypeName.getName(type) + " &";
	}

	@Override
	public void visit(TypeString type) {
		name = "const " + TypeName.getName(type) + " &";
	}

	@Override
	public void visit(TypeList type) {
		name = "const " + TypeName.getName(type) + " &";
	}

	@Override
	public void visit(TypeVector type) {
		name = "const " + TypeName.getName(type) + " &";
	}

	@Override
	public void visit(TypeSet type) {
		name = "const " + TypeName.getName(type) + " &";
	}

	@Override
	public void visit(TypeMap type) {
		name = "const " + TypeName.getName(type) + " &";
	}

	@Override
	public void visit(TypeFloat type) {
		name = TypeName.getName(type);
	}

	@Override
	public void visit(TypeDouble type) {
		name = TypeName.getName(type);
	}

	@Override
	public void visit(Cbean type) {
		name = "const " + TypeName.getName(type) + " &";
	}

	@Override
	public void visit(Xbean type) {
		name = "const " + TypeName.getName(type) + " &";
	}

	@Override
	public void visit(TypeBoolean type) {
		name = TypeName.getName(type);
	}

	@Override
	public void visit(TypeAny type) {
		throw new UnsupportedOperationException();
	}
}
