package limax.xmlgen.java;

import java.io.PrintStream;
import java.util.Collection;

import limax.xmlgen.Bean;
import limax.xmlgen.Cbean;
import limax.xmlgen.Enum;
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
import limax.xmlgen.Xbean.DynamicVariable;

public class Declare {

	public enum Type {
		PUBLIC, PRIVATE
	}

	public static void make(Variable var, Type type, PrintStream ps, String prefix) {
		ps.println(prefix + (type == Type.PUBLIC ? "public " : "private ") + TypeName.getName(var.getType()) + " "
				+ var.getName() + "; " + var.getComment());
	}

	public static void make(DynamicVariable var, Type type, PrintStream ps, String prefix) {
		ps.println(prefix + (type == Type.PUBLIC ? "public " : "private ") + TypeName.getName(var.getType()) + " "
				+ var.getName() + "; ");
	}

	private static class EnumTypeString implements Visitor {

		private final Enum e;
		private String result;

		private EnumTypeString(Enum e) {
			this.e = e;
		}

		@Override
		public void visit(TypeBoolean type) {
		}

		@Override
		public void visit(TypeByte type) {
			result = "byte " + e.getName() + " = (byte)" + e.getValue();
		}

		@Override
		public void visit(TypeShort type) {
			result = "short " + e.getName() + " = (short)" + e.getValue();
		}

		@Override
		public void visit(TypeInt type) {
			result = "int " + e.getName() + " = " + e.getValue();
		}

		@Override
		public void visit(TypeLong type) {
			result = "long " + e.getName() + " = " + e.getValue() + "L";
		}

		@Override
		public void visit(TypeFloat type) {
		}

		@Override
		public void visit(TypeDouble type) {
		}

		@Override
		public void visit(TypeBinary type) {
		}

		@Override
		public void visit(TypeString type) {
		}

		@Override
		public void visit(TypeList type) {
		}

		@Override
		public void visit(TypeSet type) {
		}

		@Override
		public void visit(TypeVector type) {
		}

		@Override
		public void visit(TypeMap type) {
		}

		@Override
		public void visit(Bean type) {
		}

		@Override
		public void visit(Cbean type) {
		}

		@Override
		public void visit(Xbean type) {
		}

		@Override
		public void visit(TypeAny type) {
		}

		static String make(Enum e) {
			EnumTypeString ets = new EnumTypeString(e);
			e.getType().accept(ets);
			return ets.result;
		}
	}

	public static void make(Enum e, PrintStream ps, String prefix) {
		ps.println(prefix + "public static final " + EnumTypeString.make(e) + "; " + e.getComment());
	}

	public static void make(Collection<Enum> enums, Collection<Variable> vars, Type type, PrintStream ps,
			String prefix) {
		enums.forEach(e -> make(e, ps, prefix));
		if (!enums.isEmpty())
			ps.println();
		vars.forEach(var -> make(var, type, ps, prefix));
		ps.println();
	}

}
