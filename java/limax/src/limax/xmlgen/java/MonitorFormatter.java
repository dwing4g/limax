package limax.xmlgen.java;

import java.io.File;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import limax.xmlgen.Bean;
import limax.xmlgen.Cbean;
import limax.xmlgen.FileOperation;
import limax.xmlgen.Key;
import limax.xmlgen.Main;
import limax.xmlgen.Monitor;
import limax.xmlgen.Monitorset;
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
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;

public class MonitorFormatter {

	private final Monitorset monitorset;

	public MonitorFormatter(Monitorset cs) {
		monitorset = cs;
	}

	public void make() {
		final boolean haskeys = !monitorset.getKeys().isEmpty();
		final String namespace = monitorset.getFirstName();
		final String name = monitorset.getName();
		File outpath = new File(Main.outputPath, "gen");
		outpath = new File(outpath, namespace.replace(".", File.separator));

		try (PrintStream ps = FileOperation.fopen(outpath, name + ".java")) {
			ps.println("package " + namespace + ";");
			ps.println();
			printImport(ps, haskeys);
			ps.println();
			ps.println("public class " + name + " {");
			if (haskeys)
				printHasKeyDefine(ps);
			else
				printNoKeyDefine(ps);
			ps.println("}");
			ps.println();
		}
	}

	private void printImport(PrintStream ps, boolean haskeys) {
		if (haskeys) {
			ps.println("import java.util.Collection;");
			ps.println("import java.util.Arrays;");
			ps.println("import java.util.HashMap;");
		} else {
			ps.println("import java.util.Collections;");
		}
		ps.println("import java.util.Map;");
		ps.println();

		ps.println("import java.sql.Connection;");
		ps.println("import java.sql.PreparedStatement;");
		ps.println("import java.sql.SQLException;");
		ps.println();

		if (haskeys) {
			ps.println("import limax.util.monitor.KeyInfo;");
			ps.println("import limax.util.monitor.GroupKeys;");
			if (null == monitorset.getMapKeyType())
				ps.println("import limax.util.monitor.MonitorSet;");
			else
				ps.println("import limax.util.monitor.MapKeySet;");
		} else {
			ps.println("import limax.util.monitor.Group;");
		}
	}

	private static void outputKeysAsVarlist(PrintStream ps, List<Key> keys, boolean boxing) {
		ps.print(
				keys.stream()
						.map(k -> TypeName.getName(k.getType(),
								boxing ? TypeName.Purpose.BOXING : TypeName.Purpose.TYPE) + " " + k.getName())
						.collect(Collectors.joining(", ")));
	}

	private static void outputMonitorsAsVarlist(PrintStream ps, List<Monitor> monitors) {
		ps.print(monitors.stream().map(m -> "long _" + m.getName()).collect(Collectors.joining(", ")));
	}

	private static void outputKeysAsnamelist(PrintStream ps, List<Key> keys) {
		ps.print(keys.stream().map(Key::getName).collect(Collectors.joining(", ")));
	}

	private static void outputKeysAsKeyInfolist(PrintStream ps, List<Key> keys) {
		boolean first = true;
		for (Key k : keys) {
			if (first)
				first = false;
			else
				ps.print(", ");
			ps.println();
			ps.println("				new KeyInfo() {");
			ps.println();
			ps.println("					@Override");
			ps.println("					public String getName() {");
			ps.println("						return \"" + k.getName() + "\";");
			ps.println("					}");
			ps.println();
			ps.println("					@Override");
			ps.println("					public Object getValue() {");
			ps.println("						return _Keys_.this." + k.getName() + ";");
			ps.println("					}");
			ps.println();
			ps.print("				}");
		}
	}

	private static void outputMethod(PrintStream ps, List<Key> keys, String monitorrname, String methodname,
			String varname, boolean hasreturn) {
		ps.print("	public static ");
		ps.print(hasreturn ? "long " : "void ");
		if (null == monitorrname)
			ps.print(methodname + "(String _name_, ");
		else
			ps.print(methodname + "_" + monitorrname + "(");
		outputKeysAsVarlist(ps, keys, false);
		if (null != varname)
			ps.print(", long " + varname);
		ps.println(") {");
		ps.print("		final _Keys_ _keys_ = new _Keys_(");
		outputKeysAsnamelist(ps, keys);
		ps.println(");");
		ps.print("		");
		if (hasreturn)
			ps.print("return ");
		ps.print("_monitorset_." + methodname + "(");
		if (null == monitorrname)
			ps.print("_name_");
		else
			ps.print("\"" + monitorrname + "\"");
		ps.print(", _keys_");
		if (null != varname)
			ps.print(", " + varname);
		ps.println(");");
		ps.println("	}");
		ps.println();
	}

	private static void outputMethod(PrintStream ps, Type cacheKeys, String monitorname, String methodname,
			String varname, boolean hasreturn) {
		if (null == cacheKeys)
			return;
		final String keysType = TypeName.getName(cacheKeys);
		ps.print("	public static ");
		ps.print(hasreturn ? "long " : "void ");
		ps.print(methodname + "_" + monitorname + "_mapkey(");
		ps.print(keysType + " _key_");
		if (null != varname)
			ps.print(", long " + varname);
		ps.println(") {");
		ps.print("		");
		if (hasreturn)
			ps.print("return ");
		ps.print("_monitorset_." + methodname + "(");
		ps.print("\"" + monitorname + "\"");
		ps.print(", _key_");
		if (null != varname)
			ps.print(", " + varname);
		ps.println(");");
		ps.println("	}");
		ps.println();
	}

	private void outputMakeObjectNameStringMethod(PrintStream ps, List<Key> keys) {
		ps.print("		static String buildObjectNameQueryString(");
		outputKeysAsVarlist(ps, keys, true);
		ps.println(") {");
		ps.println("			final StringBuilder sb = new StringBuilder();");
		ps.println("			sb.append(" + monitorset.getName() + ".class.getName()).append(\":\");");
		boolean first = true;
		for (Key k : keys) {
			ps.print("			sb.append(\"");
			if (first)
				first = false;
			else
				ps.print(",\").append(\"");
			ps.print(k.getName());
			ps.print("\").append(\"=\").append(");
			ps.print("null == ");
			ps.print(k.getName());
			ps.print(" ? \"*\" : ");
			ps.print(k.getName());
			ps.println(".toString());");
		}
		ps.println("			return sb.toString();");
		ps.println("		}");
		ps.println();
	}

	private static final class StringToObject implements Visitor {

		private String output;
		private final String input;

		private StringToObject(String input) {
			this.input = input;
		}

		private void model0(String method) {
			output = method + "(" + input + ")";
		}

		@Override
		public void visit(TypeBoolean type) {
			model0("Boolean.parseBoolean");
		}

		@Override
		public void visit(TypeByte type) {
			model0("Byte.parseByte");
		}

		@Override
		public void visit(TypeShort type) {
			model0("Short.parseShort");
		}

		@Override
		public void visit(TypeInt type) {
			model0("Integer.parseInt");
		}

		@Override
		public void visit(TypeLong type) {
			model0("Long.parseLong");
		}

		@Override
		public void visit(TypeFloat type) {
			model0("Float.parseFloat");
		}

		@Override
		public void visit(TypeDouble type) {
			model0("Double.parseDouble");
		}

		@Override
		public void visit(TypeBinary type) {
		}

		@Override
		public void visit(TypeString type) {
			output = input;
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

		public static String make(Type type, String input) {
			final StringToObject sto = new StringToObject(input);
			type.accept(sto);
			return sto.output;
		}

	}

	private static final class GetTypeString implements Visitor {

		private String output;

		@Override
		public void visit(TypeBoolean type) {
			output = "bool.class";
		}

		@Override
		public void visit(TypeByte type) {
			output = "byte.class";
		}

		@Override
		public void visit(TypeShort type) {
			output = "short.class";
		}

		@Override
		public void visit(TypeInt type) {
			output = "int.class";
		}

		@Override
		public void visit(TypeLong type) {
			output = "long.class";
		}

		@Override
		public void visit(TypeFloat type) {
			output = "float.class";
		}

		@Override
		public void visit(TypeDouble type) {
			output = "double.class";
		}

		@Override
		public void visit(TypeBinary type) {
		}

		@Override
		public void visit(TypeString type) {
			output = "String.class";
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

		public static String make(Type type) {
			final GetTypeString sto = new GetTypeString();
			type.accept(sto);
			return sto.output;
		}

	}

	private static final class GetSQLDataTypeString implements Visitor {

		private String output;

		@Override
		public void visit(TypeBoolean type) {
			output = "BOOL";
		}

		@Override
		public void visit(TypeByte type) {
			output = "TINYINT";
		}

		@Override
		public void visit(TypeShort type) {
			output = "SMALLINT";
		}

		@Override
		public void visit(TypeInt type) {
			output = "INT";
		}

		@Override
		public void visit(TypeLong type) {
			output = "BIGINT";
		}

		@Override
		public void visit(TypeFloat type) {
			output = "FLOAT";
		}

		@Override
		public void visit(TypeDouble type) {
			output = "DOUBLE";
		}

		@Override
		public void visit(TypeBinary type) {
		}

		@Override
		public void visit(TypeString type) {
			output = "VARCHAR(255)";
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

		public static String make(Type type) {
			final GetSQLDataTypeString sto = new GetSQLDataTypeString();
			type.accept(sto);
			return sto.output;
		}

	}

	private static final class GetSQLPreparedStatement implements Visitor {

		private String output;
		private final int index;
		private final String value;

		private GetSQLPreparedStatement(int index, String value) {
			this.index = index;
			this.value = value;
		}

		@Override
		public void visit(TypeBoolean type) {
			output = "setBoolean(" + index + ", Boolean.parseBoolen(" + value + "))";
		}

		@Override
		public void visit(TypeByte type) {
			output = "setByte(" + index + ", Byte.parseByte(" + value + "))";
		}

		@Override
		public void visit(TypeShort type) {
			output = "setShort(" + index + ", Short.parseShort(" + value + "))";
		}

		@Override
		public void visit(TypeInt type) {
			output = "setInt(" + index + ", Integer.parseInt(" + value + "))";
		}

		@Override
		public void visit(TypeLong type) {
			output = "setLong(" + index + ", Long.parseLong(" + value + "))";
		}

		@Override
		public void visit(TypeFloat type) {
			output = "setFloat(" + index + ", Float.parseFloat(" + value + "))";
		}

		@Override
		public void visit(TypeDouble type) {
			output = "setDouble(" + index + ", Double.parseDouble(" + value + "))";
		}

		@Override
		public void visit(TypeBinary type) {
		}

		@Override
		public void visit(TypeString type) {
			output = "setString(" + index + ", " + value + ")";
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

		public static String make(Type type, int index, String value) {
			final GetSQLPreparedStatement sto = new GetSQLPreparedStatement(index, value);
			type.accept(sto);
			return sto.output;
		}

	}

	private void outputMonitorCollectorInteface(PrintStream ps, List<Key> keys, List<Monitor> monitors) {

		if (!keys.isEmpty()) {
			ps.println("	private static final Map<String, Class<?>> mapKeyTypes = new HashMap<>();");
			ps.println("	static {");
			keys.forEach(k -> ps.println(
					"		mapKeyTypes.put(\"" + k.getName() + "\", " + GetTypeString.make(k.getType()) + ");"));
			ps.println("	}");
			ps.println();
		}

		ps.println("	@FunctionalInterface");
		ps.println("	public interface Collector {");
		ps.println();
		ps.print("		void onRecord(String host, ");
		if (!keys.isEmpty()) {
			outputKeysAsVarlist(ps, keys, false);
			ps.print(", ");
		}
		outputMonitorsAsVarlist(ps, monitors);
		ps.println(");");
		ps.println();
		ps.println("		static Object[] sortAsRecord(Map<String,String> keys, Map<String,Object> items) {");
		ps.println("			final Object[] result = {");
		keys.forEach(k -> ps.println(
				"					" + StringToObject.make(k.getType(), "keys.get(\"" + k.getName() + "\")") + ", "));
		monitors.forEach(m -> ps.println("					items.get(\"" + m.getName() + "\"), "));
		ps.println("				};");
		ps.println("			return result;");
		ps.println("		}");
		ps.println();
		ps.println("		static Map<String, Class<?>> getKeyTypes() {");
		if (keys.isEmpty()) {
			ps.println("			return Collections.emptyMap();");
		} else {
			ps.println("			return mapKeyTypes;");
		}
		ps.println("		}");
		ps.println();

		if (keys.isEmpty())
			outputMakeObjectNameStringMethod(ps);
		else
			outputMakeObjectNameStringMethod(ps, keys);

		ps.println("		static String getCreateTableString() {");
		ps.println("			return \"CREATE TABLE IF NOT EXISTS _" + monitorset.getTableName() + "(\"");
		ps.print(
				"					+ \"ts DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), host VARCHAR(255) NOT NULL");
		keys.forEach(k -> ps.print(", _" + k.getName() + " " + GetSQLDataTypeString.make(k.getType()) + " NOT NULL"));
		monitors.forEach(m -> ps.print(", _" + m.getName() + " BIGINT NOT NULL"));
		ps.println("\"");
		ps.println("					+ \");\";");
		ps.println("		}");
		ps.println();
		ps.println(
				"		static PreparedStatement createInsertStatement(Connection conn, String host, Map<String,String> keys, Map<String,Object> items) throws SQLException {");
		ps.print("			PreparedStatement pstmt = conn.prepareStatement(\"INSERT INTO _" + monitorset.getTableName()
				+ "(host");
		keys.forEach(k -> ps.print(", _" + k.getName()));
		monitors.forEach(m -> ps.print(", _" + m.getName()));
		ps.print(")VALUES(?");
		{
			final int count = keys.size() + monitors.size();
			for (int i = 0; i < count; i++)
				ps.print(", ?");
		}
		ps.println(")\");");

		ps.println("			pstmt.setString(1, host);");
		{
			int index = 2;
			for (Key k : keys) {
				ps.println("			pstmt."
						+ GetSQLPreparedStatement.make(k.getType(), index, "keys.get(\"" + k.getName() + "\")") + ";");
				index++;
			}
			for (Monitor m : monitors) {
				ps.println("			pstmt.setLong(" + index + ", (long)items.get(\"" + m.getName() + "\"));");
				index++;
			}
		}
		ps.println("			return pstmt;");
		ps.println("		}");
		ps.println();

		ps.println("	}");
		ps.println();
	}

	private void printHasKeyDefine(PrintStream ps) {
		final List<Key> keys = monitorset.getKeys();
		final List<Monitor> monitors = monitorset.getMonitors();
		final Type mapKey = monitorset.getMapKeyType();
		final boolean hasCache = null != mapKey;
		ps.println();
		ps.println("	private " + monitorset.getName() + "() {");
		ps.println("	}");
		ps.println();

		ps.println("	private static class _Keys_ implements GroupKeys {");
		keys.stream().forEach(
				k -> ps.println("		private final " + TypeName.getName(k.getType()) + " " + k.getName() + ";"));
		ps.println();
		ps.print("		_Keys_(");
		outputKeysAsVarlist(ps, keys, false);
		ps.println(") {");
		keys.stream().forEach(k -> ps.println("			this." + k.getName() + "= " + k.getName() + ";"));
		ps.println("		}");
		Equals.make(monitorset, ps, "		");
		Hashcode.make(monitorset, ps, "		");

		ps.println("		@Override");
		ps.println("		public Collection<KeyInfo> getKeys() {");
		ps.print("			return Arrays.asList( ");
		outputKeysAsKeyInfolist(ps, keys);
		ps.println(");");
		ps.println("		}");
		ps.println("	}");
		ps.println();

		{
			final String monitorType = hasCache ? ("MapKeySet<" + TypeName.getBoxingName(mapKey) + ", _Keys_>")
					: "MonitorSet<_Keys_>";
			final String createMethod = hasCache ? "MapKeySet.create" : "MonitorSet.create";
			ps.println("	private final static " + monitorType + " _monitorset_ = " + createMethod + "(");
		}

		ps.print("		" + monitorset.getName() + ".class, ");
		ps.print(monitorset.isSupportTransaction() ? "true" : "false");
		monitors.stream().forEach(c -> ps.print(", \"" + c.getName() + "\""));
		ps.println(");");
		ps.println();

		if (hasCache) {
			ps.print("	public static void mapKey(");
			ps.print(TypeName.getName(mapKey));
			ps.print(" _mapkey_, ");
			outputKeysAsVarlist(ps, keys, false);
			ps.println(") {");
			ps.print("		final _Keys_ _keys_ = new _Keys_(");
			outputKeysAsnamelist(ps, keys);
			ps.println(");");
			ps.println("		_monitorset_.mapKey(_mapkey_, _keys_);");
			ps.println("	}");
			ps.println();
		}

		monitors.stream().forEach(c -> {
			switch (c.getType()) {
			case Counter:
				outputMethod(ps, keys, c.getName(), "increment", null, false);
				outputMethod(ps, keys, c.getName(), "increment", "_delta_", false);
				outputMethod(ps, mapKey, c.getName(), "increment", null, false);
				outputMethod(ps, mapKey, c.getName(), "increment", "_delta_", false);
				break;
			case Gauge:
				outputMethod(ps, keys, c.getName(), "set", "_value_", false);
				outputMethod(ps, mapKey, c.getName(), "set", "_value_", false);
				outputMethod(ps, keys, c.getName(), "increment", "_delta_", false);
				outputMethod(ps, mapKey, c.getName(), "increment", "_delta_", false);
				break;
			default:
				break;
			}
			outputMethod(ps, keys, c.getName(), "get", null, true);
		});

		outputMonitorCollectorInteface(ps, keys, monitors);
	}

	private static void outputMethod(PrintStream ps, String monitorname, String methodname, String varname,
			boolean hasreturn) {
		ps.print("	public static ");
		ps.print(hasreturn ? "long " : "void ");
		ps.print(methodname + "_" + monitorname + "(");
		if (null != varname)
			ps.print("long " + varname);
		ps.println(") {");
		ps.print("		");
		if (hasreturn)
			ps.print("return ");
		ps.print("_group_." + methodname + "(");
		ps.print("\"" + monitorname + "\"");
		if (null != varname)
			ps.print(", " + varname);
		ps.println(");");
		ps.println("	}");
		ps.println();
	}

	private void outputMakeObjectNameStringMethod(PrintStream ps) {
		ps.println("		public static String getObjectNameQueryString() {");
		ps.println("			return \"" + monitorset.getFirstName() + ":type=" + monitorset.getName() + "\";");
		ps.println("		}");
		ps.println();

	}

	private void printNoKeyDefine(PrintStream ps) {
		final List<Monitor> monitors = monitorset.getMonitors();
		ps.println();
		ps.println("	private " + monitorset.getName() + "() {");
		ps.println("	}");
		ps.println();

		ps.print("	private final static Group _group_ = Group.create( " + monitorset.getName() + ".class, ");
		ps.print(monitorset.isSupportTransaction() ? "true" : "false");
		monitors.stream().forEach(c -> ps.print(", \"" + c.getName() + "\""));
		ps.println(");");
		ps.println();

		monitors.stream().forEach(c -> {
			switch (c.getType()) {
			case Counter:
				outputMethod(ps, c.getName(), "increment", null, false);
				outputMethod(ps, c.getName(), "increment", "_delta_", false);
				break;
			case Gauge:
				outputMethod(ps, c.getName(), "set", "_value_", false);
				outputMethod(ps, c.getName(), "increment", "_delta_", false);
				break;
			default:
				break;
			}
			outputMethod(ps, c.getName(), "get", null, true);
		});

		outputMonitorCollectorInteface(ps, Collections.emptyList(), monitors);
	}
}
