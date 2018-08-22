package limax.xmlgen.java;

import java.io.PrintStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import limax.util.StringUtils;
import limax.xmlgen.Cbean;
import limax.xmlgen.Procedure;
import limax.xmlgen.Table;
import limax.xmlgen.Type;
import limax.xmlgen.Variable;
import limax.xmlgen.Xbean;
import limax.xmlgen.Xbean.DynamicVariable;
import limax.xmlgen.Zdb;

class TableMeta {

	static void make(Zdb zdb) {
		Procedure proc = zdb.getProcedure();
		Collection<Table> tables = zdb.getTables();
		Set<Type> types = new HashSet<>();
		tables.forEach(t -> t.depends(types));
		Collection<Xbean> xbeans = types.stream().filter(t -> t instanceof Xbean).map(t -> (Xbean) t)
				.collect(Collectors.toList());
		Collection<Cbean> cbeans = types.stream().filter(t -> t instanceof Cbean).map(t -> (Cbean) t)
				.collect(Collectors.toList());
		try (PrintStream ps = Zdbgen.openTableFile("_Meta_")) {

			ps.println("package table;");
			ps.println();

			if (!cbeans.isEmpty())
				ps.println("import limax.xmlgen.Cbean;");
			if (!xbeans.isEmpty())
				ps.println("import limax.xmlgen.Xbean;");
			if (!tables.isEmpty())
				ps.println("import limax.xmlgen.Table;");
			ps.println("import limax.xmlgen.Procedure;");
			ps.println("import limax.xmlgen.Zdb;");

			if (!cbeans.isEmpty() || !xbeans.isEmpty())
				ps.println("import limax.xmlgen.Variable;");

			ps.println();
			ps.println("final class _Meta_ {");
			ps.println("	private _Meta_(){}");
			ps.println();

			ps.println("	public static Zdb create() {");
			ps.println("		Zdb.Builder _b_ = new Zdb.Builder(new limax.xmlgen.Naming.Root());");
			if (zdb.isDynamic())
				ps.println("		_b_.dynamic();");
			if (!zdb.getDefaultTableCache().equals("limax.zdb.TTableCacheLRU"))
				ps.println("		_b_.defaultTableCache(" + StringUtils.quote(zdb.getDefaultTableCache()) + ");");
			if (zdb.isZdbVerify())
				ps.println("		_b_.zdbVerify(true);");
			if (zdb.getAutoKeyInitValue() != -1)
				ps.println("		_b_.autoKeyInitValue(" + zdb.getAutoKeyInitValue() + ").autoKeyStep("
						+ zdb.getAutoKeyStep() + ");");
			ps.println("		_b_.corePoolSize(" + zdb.getCorePoolSize() + ");");
			ps.println("		_b_.procPoolSize(" + zdb.getProcPoolSize() + ");");
			ps.println("		_b_.schedPoolSize(" + zdb.getSchedPoolSize() + ");");
			ps.println("		_b_.checkpointPeriod(" + zdb.getCheckpointPeriod() + ");");
			ps.println("		_b_.deadlockDetectPeriod(" + zdb.getDeadlockDetectPeriod() + ");");
			ps.println("		_b_.snapshotFatalTime(" + zdb.getSnapshotFatalTime() + ");");
			if (zdb.getMarshalPeriod() != -1)
				ps.println("		_b_.marshalPeriod(" + zdb.getMarshalPeriod() + ");");
			if (zdb.getMarshalN() != 1)
				ps.println("		_b_.marshalN(" + zdb.getMarshalN() + ");");
			ps.println("		_b_.edbCacheSize(" + zdb.getEdbCacheSize() + ");");
			ps.println("		_b_.edbLoggerPages(" + zdb.getEdbLoggerPages() + ");");
			ps.println("		Zdb _meta_ = _b_.build();");
			ps.println();

			ps.println("		new Procedure.Builder(_meta_)"
					+ ((proc.getMaxExecutionTime() != 0) ? ".maxExecutionTime(" + proc.getMaxExecutionTime() + ")" : "")
					+ ".retryTimes(" + proc.getRetryTimes() + ").retryDelay(" + proc.getRetryDelay() + ").retrySerial("
					+ proc.getRetrySerial() + ")" + ((proc.getTrace() != limax.util.Trace.WARN)
							? ".trace(limax.util.Trace." + proc.getTrace() + ");" : ";"));
			ps.println();

			cbeans.forEach(cbean -> {
				if (cbean.getVariables().isEmpty()) {
					ps.println("		new Cbean(_meta_, " + StringUtils.quote(cbean.getName()) + ");");
				} else {
					String name = "c" + cbean.getName();
					ps.println("		Cbean " + name + " = new Cbean(_meta_, " + StringUtils.quote(cbean.getName())
							+ ");");
					cbean.getVariables().forEach(var -> ps.println("		" + varMeta(var, name) + ";"));
				}
				ps.println();
			});
			ps.println();

			xbeans.forEach(xbean -> {
				String construct = "new Xbean(_meta_, " + StringUtils.quote(xbean.getName()) + ");";
				if (xbean.getVariables().isEmpty()) {
					ps.println("		" + construct);
				} else {
					String name = "x" + xbean.getName();
					ps.println("		Xbean " + name + " = " + construct);
					xbean.getVariables().forEach(var -> {
						String s = varMeta(var, name);
						if (var.isDynamic()) {
							Set<Integer> serialsInUse = xbean.getDynamicVariables().stream()
									.map(DynamicVariable::getSerial).collect(Collectors.toSet());
							String dname = name + "_" + var.getName();
							ps.println("		Variable " + dname + " = " + s + ".build();");
							var.getChildren().stream().map(obj -> (Variable) obj)
									.filter(dvar -> serialsInUse.contains(dvar.getSerial()))
									.forEach(dvar -> ps.println("		" + varMeta(dvar, dname) + ";"));
						} else {
							ps.println("		" + s + ";");
						}
					});
				}
				ps.println();
			});
			ps.println();

			tables.forEach(table -> tableMeta(table, ps, "		"));
			ps.println();
			ps.println("		return _meta_;");
			ps.println("	}");

			ps.println("}");
		}
	}

	private static void tableMeta(Table table, PrintStream ps, String prefix) {
		StringBuilder sb = new StringBuilder();
		sb.append("new Table.Builder(_meta_, ");
		sb.append(StringUtils.quote(table.getName()) + ", ");
		sb.append(StringUtils.quote(table.getKey()) + ", ");
		sb.append(StringUtils.quote(table.getValue()));

		if (table.isMemory())
			sb.append(").memory(true");
		if (table.isAutoIncrement())
			sb.append(").autoIncrement(true");

		if (!table.getLock().isEmpty())
			sb.append(").lock(" + StringUtils.quote(table.getLock()));
		if (!table.getCacheCap().isEmpty())
			sb.append(").cacheCap(" + StringUtils.quote(table.getCacheCap()));

		if (!table.getForeign().isEmpty())
			sb.append(").foreign(" + StringUtils.quote(table.getForeign()));
		if (!table.getCapacity().isEmpty())
			sb.append(").capacity(" + StringUtils.quote(table.getCapacity()));

		sb.append(");");
		ps.println(prefix + sb.toString());
	}

	private static String varMeta(Variable var, String parent) {
		StringBuilder sb = new StringBuilder();
		sb.append("new Variable.Builder(");
		sb.append(parent + ", ");
		sb.append(StringUtils.quote(var.getName()) + ", ");
		sb.append(StringUtils.quote(var.getTypeString()));
		sb.append(")");
		if (var.isDynamic())
			sb.append(".dynamic()");
		else {
			if (!var.getKey().isEmpty())
				sb.append(".key(" + StringUtils.quote(var.getKey()) + ")");
			if (!var.getValue().isEmpty())
				sb.append(".value(" + StringUtils.quote(var.getValue()) + ")");
			if (!var.getForeign().isEmpty())
				sb.append(".foreign(" + StringUtils.quote(var.getForeign()) + ")");
			if (!var.getCapacity().isEmpty())
				sb.append(".capacity(" + StringUtils.quote(var.getCapacity()) + ")");
			if (var.getSerial() != -1)
				sb.append(".serial(" + var.getSerial() + ")");
			if (!var.getScript().isEmpty())
				sb.append(".script(" + StringUtils.quote(var.getScript()) + ")");
		}
		return sb.toString();
	}

}
