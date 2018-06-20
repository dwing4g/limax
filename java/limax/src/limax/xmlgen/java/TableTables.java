package limax.xmlgen.java;

import java.io.PrintStream;
import java.util.Collection;

import limax.util.StringUtils;
import limax.xmlgen.Table;
import limax.xmlgen.Xbean;

class TableTables {

	static void make(Collection<Table> tables) {
		String classname = "_Tables_";
		try (PrintStream ps = Zdbgen.openTableFile(classname)) {

			ps.println("package table;");
			ps.println();
			if (!tables.isEmpty()) {
				ps.println("import limax.codec.OctetsStream;");
				ps.println("import limax.codec.MarshalException;");
			}
			ps.println();
			ps.println("final class _Tables_ {");
			ps.println("	volatile static _Tables_ instance;");
			ps.println();
			ps.println("	private _Tables_ () {");
			ps.println("		instance = this;");
			ps.println("	}");
			ps.println();

			for (Table table : tables)
				define(table, ps);
			ps.println();
			ps.println("}");
		}
	}

	static void define(Table table, PrintStream ps) {
		String name = table.getName();
		String k = TypeName.getBoxingName(table.getKeyType());
		String v = TypeName.getBoxingName(table.getValueType());
		String t = "limax.zdb.TTable<" + k + ", " + v + ">";

		ps.println("	class " + StringUtils.upper1(name) + " extends " + t + " {");
		ps.println("		@Override");
		ps.println("		public String getName() {");
		ps.println("			return " + StringUtils.quote(name) + ";");
		ps.println("		}");
		ps.println();

		ps.println("		@Override");
		ps.println("		protected OctetsStream marshalKey(" + k + " key) {");
		if (table.isMemory()) {
			ps.println("			throw new UnsupportedOperationException();");
		} else {
			ps.println("			OctetsStream _os_ = new OctetsStream();");
			Marshal.make(table.getKeyType(), "key", ps, "			");
			ps.println("			return _os_;");
		}
		ps.println("		}");
		ps.println();
		ps.println("		@Override");
		ps.println("		protected OctetsStream marshalValue(" + v + " value) {");
		if (table.isMemory()) {
			ps.println("			throw new UnsupportedOperationException();");
		} else {
			ps.println("			OctetsStream _os_ = new OctetsStream();");
			Marshal.make(table.getValueType(), "value", ps, "			");
			ps.println("			return _os_;");
		}
		ps.println("		}");
		ps.println();

		ps.println("		@Override");
		ps.println("		protected " + k + " unmarshalKey(OctetsStream _os_) throws MarshalException {");
		if (table.isMemory()) {
			ps.println("			throw new UnsupportedOperationException();");
		} else {
			ConstructWithUnmarshal.make(table.getKeyType(), "key", ps, "			");
			ps.println("			return key;");
		}
		ps.println("		}");
		ps.println();

		ps.println("		@Override");
		ps.println("		protected " + v + " unmarshalValue(OctetsStream _os_) throws MarshalException {");
		if (table.isMemory()) {
			ps.println("			throw new UnsupportedOperationException();");
		} else {
			ConstructWithUnmarshal.make(table.getValueType(), "value", ps, "			");
			ps.println("			return value;");
		}
		ps.println("		}");
		ps.println();
		ps.println("		@Override");
		ps.println("		protected " + v + " newValue() {");
		Define.beginInitial(true);
		Define.make(table.getValueType(), "value", ps, "			");
		Define.endInitial();
		ps.println("			return value;");
		ps.println("		}");
		ps.println();

		if (table.getValueType() instanceof Xbean) {
			ps.println("		" + v + " insert(" + k + " key) {");
			ps.println("			" + v + " value = new " + v + "();");
			ps.println("			return add(key, value) ? value : null;");
			ps.println("		}");
			ps.println();
			if (table.isAutoIncrement()) {
				ps.println("		" + k + " newKey() {");
				ps.println("			return nextKey();");
				ps.println("		}");
				ps.println();
				ps.println("		limax.util.Pair<Long, " + v + "> insert() {");
				ps.println("			Long next = nextKey();");
				ps.println("			return new limax.util.Pair<Long, " + v + ">(next, insert(next));");
				ps.println("		}");
				ps.println();
			}
		} else {
			ps.println("		" + v + " insert(" + k + " key, " + v + " value) {");
			ps.println("			return add(key, value) ? value : null;");
			ps.println("		}");
			ps.println();
			if (table.isAutoIncrement()) {
				ps.println("		" + k + " newKey() {");
				ps.println("			return nextKey();");
				ps.println("		}");
				ps.println();
				ps.println("		limax.util.Pair<Long, " + v + "> insert(" + v + " value) {");
				ps.println("			Long next = nextKey();");
				ps.println("			return new limax.util.Pair<Long, " + v + ">(next, insert(next, value));");
				ps.println("		}");
				ps.println();
			}
		}
		ps.println("		" + v + " update(" + k + " key) {");
		ps.println("			return get(key, true);");
		ps.println("		}");
		ps.println();
		ps.println("		" + v + " select(" + k + " key) {");
		ps.println("			return get(key, false);");
		ps.println("		}");
		ps.println();
		ps.println("		boolean delete(" + k + " key) {");
		ps.println("			return remove(key);");
		ps.println("		}");
		ps.println();
		ps.println("	};");
		ps.println();
		ps.println("	" + StringUtils.upper1(name) + " " + name + " = new " + StringUtils.upper1(name) + "();");
		ps.println();
	}
}
