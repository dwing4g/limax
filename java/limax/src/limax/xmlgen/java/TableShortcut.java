package limax.xmlgen.java;

import java.io.PrintStream;

import limax.util.StringUtils;
import limax.xmlgen.Table;
import limax.xmlgen.Xbean;

class TableShortcut {

	static void make(Table table) {
		new TableShortcut(table).make();
	}

	private Table table;

	TableShortcut(Table table) {
		this.table = table;
	}

	void make() {
		String name = table.getName();
		String classname = StringUtils.upper1(name);
		try (PrintStream ps = Zdbgen.openTableFile(classname)) {

			String K = TypeName.getBoxingName(table.getKeyType());
			String V = TypeName.getBoxingName(table.getValueType());
			String KV = "<" + K + ", " + V + ">";

			ps.println("package table;");
			ps.println();
			ps.println("public class " + classname + " {");
			ps.println("	private " + classname + "() {");
			ps.println("	}");
			ps.println();

			if (table.getValueType() instanceof Xbean) {
				ps.println("	public static " + V + " insert(" + K + " key) {");
				ps.println("		return _Tables_.instance." + name + ".insert(key);");
				ps.println("	}");
				ps.println();
				if (table.isAutoIncrement()) {
					ps.println("	public static " + K + " newKey() {");
					ps.println("		return _Tables_.instance." + name + ".newKey();");
					ps.println("	}");
					ps.println();
					ps.println("	public static limax.util.Pair<Long, " + V + "> insert() {");
					ps.println("		return _Tables_.instance." + name + ".insert();");
					ps.println("	}");
					ps.println();
				}
			} else {
				ps.println("	public static " + V + " insert(" + K + " key, " + V + " value) {");
				ps.println("		return _Tables_.instance." + name + ".insert(key, value);");
				ps.println("	}");
				ps.println();
				if (table.isAutoIncrement()) {
					ps.println("	public static " + K + " newKey() {");
					ps.println("		return _Tables_.instance." + name + ".newKey();");
					ps.println("	}");
					ps.println();
					ps.println("	public static limax.util.Pair<Long, " + V + "> insert(" + V + " value) {");
					ps.println("		return _Tables_.instance." + name + ".insert(value);");
					ps.println("	}");
					ps.println();
				}
			}
			ps.println("	public static " + V + " update(" + K + " key) {");
			ps.println("		return _Tables_.instance." + name + ".update(key);");
			ps.println("	}");
			ps.println();
			ps.println("	public static " + V + " select(" + K + " key) {");
			ps.println("		return _Tables_.instance." + name + ".select(key);");
			ps.println("	}");
			ps.println();
			ps.println("	public static boolean delete(" + K + " key) {");
			ps.println("		return _Tables_.instance." + name + ".delete(key);");
			ps.println("	}");
			ps.println();
			ps.println("	public static limax.zdb.TTable" + KV + " get() {");
			ps.println("		return _Tables_.instance." + name + ";");
			ps.println("	}");
			ps.println();
			ps.println("}");
		}
	}
}
