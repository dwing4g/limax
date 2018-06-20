package limax.xmlgen.csharp;

import java.io.File;
import java.io.PrintStream;
import java.util.Collection;

import limax.xmlgen.Bean;
import limax.xmlgen.Cbean;
import limax.xmlgen.Enum;
import limax.xmlgen.FileOperation;
import limax.xmlgen.Protocol;
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
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;

class BeanFormatter {

	static final String baseClassName = "limax.codec.Marshal";

	private final Bean bean;

	BeanFormatter(Bean bean) {
		this.bean = bean;
	}

	BeanFormatter(Protocol protocol) {
		this.bean = protocol.getImplementBean();
	}

	static void printCommonInclude(PrintStream ps) {
		ps.println("using System;");
		ps.println("using System.Collections.Generic;");
		ps.println("using limax.codec;");
		ps.println("using limax.util;");
	}

	static String getOutputFileName(String fullname) {
		final int index = fullname.indexOf('.');
		if (-1 == index)
			return fullname;
		else
			return fullname.substring(index + 1);
	}

	void make(File output) {
		try (PrintStream ps = FileOperation.fopen(output, getOutputFileName(bean.getFullName()) + ".cs")) {
			printCommonInclude(ps);
			ps.println("namespace " + bean.getFirstName());
			ps.println("{");
			String baseclass = baseClassName;
			if (bean.isConstType())
				baseclass += ", IComparable<" + bean.getName() + ">";
			if (bean.isJSONEnabled())
				baseclass += ", JSONMarshal";
			ps.println("	public sealed class " + bean.getName() + " : " + baseclass);
			ps.println("	{");
			printDefines(ps);
			ps.println("	}");
			ps.println("}");
		}
	}

	private void printDefines(PrintStream ps) {
		declareEnums(ps);
		Define.make(bean, ps, "		");
		Construct.make(bean, ps, "		");
		ConstructWithParam.make(bean, ps, "		");
		Marshal.make(bean, ps, "		");
		Unmarshal.make(bean, ps, "		");
		if (bean.isConstType())
			CompareTo.make(bean, ps, "		");
		if (bean.isJSONEnabled())
			JSONMarshal.make(bean, ps, "		");
		Equals.make(bean, ps, "		");
		Hashcode.make(bean, ps, "		");
	}

	private void declareEnums(PrintStream ps) {
		declareEnums(ps, bean.getEnums());
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
			result = "byte " + e.getName() + " = " + e.getValue();
		}

		@Override
		public void visit(TypeShort type) {
			result = "short " + e.getName() + " = " + e.getValue();
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

	static void declareEnums(PrintStream ps, Collection<Enum> enums) {
		if (enums.isEmpty())
			return;
		for (Enum e : enums)
			ps.println("		public const " + EnumTypeString.make(e) + ";" + e.getComment());
	}
}
