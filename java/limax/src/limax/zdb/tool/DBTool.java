package limax.zdb.tool;

import java.nio.file.Path;
import java.nio.file.Paths;

import limax.util.InteractiveTool;
import limax.xmlgen.Table;
import limax.xmlgen.Zdb;
import limax.zdb.DBC;

/**
 * <p>
 * a toolset to show schema, dump data. show memory usage, and convert data when
 * schema changed. you can script it
 * 
 * <pre>
 * echo -e "[cmd2]\n[cmd2]\n..." | java ...
 * </pre>
 * <p>
 * commands:
 * <p>
 * dbs [path], #default .
 * <p>
 * use [db]
 * <p>
 * tables
 * <p>
 * schema [table]
 * <p>
 * dump [table]
 * <p>
 * memory [orderBy] #name|table|cache|capacity, default name
 * <p>
 * convert [toDB] #default zdbcov
 * <p>
 * out [filename] [charset] #default System.out, UTF-8
 * <p>
 * help
 * <p>
 * exit / quit
 * <p>
 * use .memory .convert in jar with generated table._Meta_ inside.
 */
public class DBTool extends InteractiveTool {

	public static void main(String[] args) throws Exception {
		new DBTool().interactive(args);
	}

	private DBTool() {
	}

	@Override
	protected void eval(String[] words) throws Exception {
		switch (words[0]) {
		case "tables":
			if (words.length != 2) {
				out.println("tables <db>");
				break;
			}
			for (limax.xmlgen.Table t : Zdb.loadFromDb(words[1]).getTables())
				out.println(t.getName());
			out.flush();
			break;
		case "schema":
			if (words.length != 3) {
				out.println("schema <db> <table>");
				break;
			}
			Table table = Zdb.loadFromDb(words[1]).getTable(words[2]);
			if (table == null) {
				out.println("table " + words[1] + " not exist");
				break;
			}
			out.println(Schemas.of(table));
			out.flush();
			break;
		case "dump":
			if (words.length != 3) {
				out.println("dump <db> <table>");
				break;
			}
			DBC.start();
			DBC dbc = DBC.open(Zdb.loadFromDb(words[1]));
			DBC.Table t = dbc.openTable(words[2]);
			if (t == null) {
				out.println("table " + words[2] + " not exist");
				break;
			}
			DataWalker.walk(t, kv -> {
				out.println(kv.getKey() + ", " + kv.getValue());
				return true;
			});
			t.close();
			DBC.stop();
			out.flush();
			break;
		case "memory":
			String orderBy = "name";
			if (words.length == 2) {
				orderBy = words[1];
			} else if (words.length != 1) {
				out.println("memory [orderBy] #name|table|cache|capacity, default name");
				break;
			}
			DBMemory.dump(Zdb.loadFromClass(), orderBy, out);
			break;
		case "convert":
			String fromDB = "zdb";
			String toDB = "zdbcov";
			boolean autoConvertWhenMaybeAuto = false;
			boolean generateSolver = false;
			if (words.length > 5) {
				out.println(
						"convert [fromDB [toDB [autoConvertWhenMaybeAuto [generateSolver]]]] #default zdb zdbcov false false");
				break;
			}
			if (words.length > 4) {
				generateSolver = words[4].equals("true");
			}
			if (words.length > 3) {
				autoConvertWhenMaybeAuto = words[3].equals("true");
			}
			if (words.length > 2) {
				toDB = words[2];
			}
			if (words.length > 1) {
				fromDB = words[1];
			}
			if (Zdb.getEngineType(fromDB) == Zdb.EngineType.EDB && Zdb.getEngineType(toDB) == Zdb.EngineType.EDB) {
				Path sourcePath = Paths.get(fromDB);
				Path targetPath = Paths.get(toDB);
				if (sourcePath.equals(targetPath)) {
					out.println("fromDB and toDB is same.");
					break;
				}
			}
			if (Zdb.getEngineType(fromDB) == Zdb.EngineType.MYSQL && Zdb.getEngineType(toDB) == Zdb.EngineType.MYSQL
					&& fromDB.substring(0, fromDB.indexOf("?"))
							.equalsIgnoreCase(toDB.substring(0, toDB.indexOf("?")))) {
				out.println("fromDB and toDB is same.");
				break;
			}
			DBConvert.convert(fromDB, toDB, autoConvertWhenMaybeAuto, generateSolver, out);
			break;
		default:
			out.println("tables <db>");
			out.println("schema <table>");
			out.println("dump <table>");
			out.println("memory [orderBy] #name|table|cache|capacity, default name");
			out.println(
					"convert [fromDB [toDB [autoConvertWhenMaybeAuto [generateSolver]]]] #default zdb zdbcov false false");
			help();
			break;
		}
	}
}
