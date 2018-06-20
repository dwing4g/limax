package limax.zdb.tool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import limax.util.StringUtils;
import limax.xmlgen.Cbean;
import limax.xmlgen.Main;
import limax.xmlgen.Table;
import limax.xmlgen.Type;
import limax.xmlgen.Variable;
import limax.xmlgen.Xbean;
import limax.xmlgen.Xbean.DynamicVariable;
import limax.xmlgen.Zdb;
import limax.xmlgen.java.Construct;
import limax.xmlgen.java.ConstructWithUnmarshal;
import limax.xmlgen.java.Declare;
import limax.xmlgen.java.Define;
import limax.xmlgen.java.Marshal;
import limax.xmlgen.java.Unmarshal;
import limax.zdb.DBC;

class DBConvert {

	public static void convert(String sourcePath, String targetPath, boolean autoConvertWhenMaybeAuto,
			boolean generateSolver, PrintStream out) throws Exception {
		Zdb targetZdbMeta = Zdb.loadFromClass();
		Zdb sourceZdbMeta = Zdb.loadFromDb(sourcePath);
		targetZdbMeta.setDbHome(targetPath);

		List<String> needGenerateConverters = new ArrayList<>();
		List<String> needGenerateSolvers = new ArrayList<>();

		Convert.diff(sourceZdbMeta, targetZdbMeta, true, null).forEach((tableName, type) -> {
			out.println(tableName + " " + type);
			switch (type) {
			case SAME:
			case AUTO:
				if (targetZdbMeta.isDynamic() && targetZdbMeta.getTable(tableName).getValueType() instanceof Xbean)
					needGenerateConverters.add(tableName);
				break;
			case MAYBE_AUTO:
				if (!autoConvertWhenMaybeAuto)
					needGenerateConverters.add(tableName);
				break;
			case MANUAL:
				needGenerateConverters.add(tableName);
				break;
			}
			if (generateSolver)
				needGenerateSolvers.add(tableName);
		});

		if (needGenerateConverters.isEmpty() && !generateSolver) {
			out.println("-----no need generate!, auto convert start-----");
			DBC.start();
			Convert.convert(DBC.open(sourceZdbMeta), DBC.open(targetZdbMeta), null, null, autoConvertWhenMaybeAuto,
					out);
			DBC.stop();
			out.println("-----auto convert end-----");
		} else {
			try {
				Method method = Class.forName("COV").getMethod("convert", DBC.class, DBC.class);
				out.println("-----COV.class found, manual convert start-----");
				try {
					DBC.start();
					DBC sourceDBC = DBC.open(sourceZdbMeta);
					DBC targetDBC = DBC.open(targetZdbMeta);
					method.invoke(null, sourceDBC, targetDBC);
					DBC.stop();
				} catch (Exception e) {
					e.printStackTrace(out);
				}
				out.println("-----manual convert end-----");
				return;
			} catch (Exception e) {
				out.println("-----COV.class not found, generate-----");
			}
			Path dir = Paths.get("cov");
			if (dir.toFile().mkdir())
				out.println("make dir [cov]");
			generate(dir.resolve("convert"), needGenerateConverters, out,
					tableName -> ps -> new DBConvert(sourceZdbMeta, targetZdbMeta, tableName).generateConverter(ps));
			generate(dir.resolve("solver"), needGenerateSolvers, out,
					tableName -> ps -> new DBConvert(sourceZdbMeta, targetZdbMeta, tableName).generateSolver(ps));
			generate(dir, needGenerateConverters, needGenerateSolvers, autoConvertWhenMaybeAuto, out);
		}
	}

	private static void generate(Path path, Collection<String> tableNames, PrintStream out,
			Function<String, Consumer<PrintStream>> toConvert) {
		if (tableNames.isEmpty())
			return;
		if (path.toFile().mkdir())
			out.println("make dir [" + path + "]");
		tableNames.forEach(tableName -> {
			File file = path.resolve(StringUtils.upper1(tableName) + ".java").toFile();
			if (file.exists())
				out.println("skip exist file " + file);
			else {
				out.println("generating " + file);
				try (PrintStream ps = new PrintStream(new FileOutputStream(file), true, "UTF-8")) {
					toConvert.apply(tableName).accept(ps);
				} catch (Exception e) {
					e.printStackTrace(out);
				}
			}
		});
	}

	private static void generate(Path dir, Collection<String> needGenerateConverters,
			Collection<String> needGenerateSolvers, boolean autoConvertWhenMaybeAuto, PrintStream out) {
		File file = dir.resolve("COV.java").toFile();
		if (file.exists())
			out.println("skip exist file " + file);
		else {
			out.println("generating " + file);
			try (PrintStream ps = new PrintStream(new FileOutputStream(file), true, "UTF-8")) {
				ps.println("import java.util.LinkedHashMap;");
				ps.println("import java.util.Map;");
				ps.println("import limax.zdb.DBC;");
				ps.println("import limax.zdb.tool.ConflictSolver;");
				ps.println("import limax.zdb.tool.Converter;");
				ps.println("import limax.zdb.tool.Convert;");
				ps.println();
				ps.println("public class COV {");
				ps.println("    public static void convert(DBC sourceDBC, DBC targetDBC) {");
				ps.println("        Map<String, Converter> converterMap = new LinkedHashMap<>();");
				ps.println("        Map<String, ConflictSolver> solverMap = new LinkedHashMap<>();");
				needGenerateConverters.forEach(tableName -> ps.println("        converterMap.put(\"" + tableName
						+ "\", convert." + StringUtils.upper1(tableName) + ".INSTANCE);"));
				needGenerateSolvers.forEach(tableName -> ps.println("        solverMap.put(\"" + tableName
						+ "\", solver." + StringUtils.upper1(tableName) + ".INSTANCE);"));
				ps.println("        Convert.convert(sourceDBC, targetDBC, converterMap, solverMap, "
						+ autoConvertWhenMaybeAuto + ", System.out);");
				ps.println("    }");
				ps.println("}");
			} catch (Exception e) {
				e.printStackTrace(out);
			}
		}
	}

	private final Zdb sourceZdbMeta;
	private final Zdb targetZdbMeta;
	private final Table sourceMeta;
	private final Table targetMeta;
	private final String className;
	private final SchemaKeyValue sourceSchema;
	private final SchemaKeyValue targetSchema;
	private final Map<String, SchemaBean> sourceBeans = new HashMap<>();
	private final Map<String, SchemaBean> targetBeans = new HashMap<>();
	private final Map<String, SchemaBean> allTargetBeans;
	private final Set<String> sameBeans;

	private DBConvert(Zdb sourceZdbMeta, Zdb targetZdbMeta, String tableName) {
		this.sourceZdbMeta = sourceZdbMeta;
		this.targetZdbMeta = targetZdbMeta;
		className = StringUtils.upper1(tableName);
		sourceMeta = sourceZdbMeta.getTable(tableName);
		targetMeta = targetZdbMeta.getTable(tableName);
		sourceSchema = Schemas.of(sourceMeta);
		targetSchema = Schemas.of(targetMeta);
		findAllChildBean(sourceSchema, sourceBeans);
		findAllChildBean(targetSchema, targetBeans);
		allTargetBeans = new HashMap<>(targetBeans);
		sameBeans = sourceBeans.values().stream().filter(sourceBean -> {
			SchemaBean targetBean = targetBeans.get(sourceBean.typeName());
			return targetBean != null && sourceBean.isDynamic() == targetBean.isDynamic()
					&& Schema.equals(sourceBean, targetBean);
		}).map(SchemaBean::typeName).collect(Collectors.toSet());
		sourceBeans.keySet().removeAll(sameBeans);
		targetBeans.keySet().removeAll(sameBeans);
	}

	private void generateConverter(PrintStream ps) {
		ps.println("package convert;");
		ps.println();
		ps.println("import limax.codec.OctetsStream;");
		ps.println("import limax.codec.MarshalException;");
		ps.println();
		ps.println("public enum " + className + " implements limax.zdb.tool.Converter {");
		ps.println("	INSTANCE;");
		ps.println();
		if (!sameBeans.isEmpty()) {
			sameBeans.forEach(name -> formatBean(sourceZdbMeta, name, false,
					EnumSet.of(BeanOption.MARSHAL, BeanOption.UNMARSHAL), ps, "	"));
			ps.println();
		}
		if (!sourceBeans.isEmpty()) {
			ps.println("	static class SS {");
			ps.println();
			sourceBeans.keySet().forEach(
					name -> formatBean(sourceZdbMeta, name, false, EnumSet.of(BeanOption.UNMARSHAL), ps, "		"));
			ps.println("	}");
			ps.println();
		}
		if (!targetBeans.isEmpty()) {
			ps.println("	static class TT {");
			ps.println();
			targetBeans.keySet().forEach(
					name -> formatBean(targetZdbMeta, name, true, EnumSet.of(BeanOption.MARSHAL), ps, "		"));
			ps.println("	}");
			ps.println();
		}
		formatConvert(true, ps, "	");
		formatConvert(false, ps, "	");
		ps.println("}");
	}

	private void generateSolver(PrintStream ps) {
		ps.println("package solver;");
		ps.println();
		ps.println("import limax.codec.Octets;");
		ps.println("import limax.codec.OctetsStream;");
		ps.println("import limax.codec.MarshalException;");
		ps.println();
		ps.println("public enum " + className + " implements limax.zdb.tool.ConflictSolver {");
		ps.println("	INSTANCE;");
		ps.println();
		allTargetBeans.keySet().forEach(name -> formatBean(targetZdbMeta, name, false,
				EnumSet.of(BeanOption.MARSHAL, BeanOption.UNMARSHAL), ps, "    "));
		if (!(targetMeta.getKeyType() instanceof Cbean))
			ps.println("	@SuppressWarnings(\"unused\")");
		ps.println("	@Override");
		ps.println(
				"	public Octets solve(Octets sourceValue, Octets targetValue, Octets key) throws MarshalException {");
		ps.println("		OctetsStream _os_ = OctetsStream.wrap(sourceValue);");
		ConstructWithUnmarshal.make(targetMeta.getValueType(), "s", ps, "		");
		ps.println("		_os_ = OctetsStream.wrap(targetValue);");
		ConstructWithUnmarshal.make(targetMeta.getValueType(), "t", ps, "		");
		ps.println("		_os_ = OctetsStream.wrap(key);");
		ConstructWithUnmarshal.make(targetMeta.getKeyType(), "k", ps, "		");
		Define.beginInitial(true);
		Define.make(targetMeta.getValueType(), "res", ps, "		");
		Define.endInitial();
		ps.println("		//TODO ");
		ps.println("		_os_ = new OctetsStream();");
		Marshal.make(targetMeta.getValueType(), "res", ps, "		");
		ps.println("		return _os_;");
		ps.println("	}");
		ps.println();
		ps.println("}");
	}

	private void formatConvert(boolean isKey, PrintStream ps, String prefix) {
		Schema sourceSchema = isKey ? this.sourceSchema.keySchema() : this.sourceSchema.valueSchema();
		Schema targetSchema = isKey ? this.targetSchema.keySchema() : this.targetSchema.valueSchema();
		ps.println(prefix + "@Override");
		ps.println(prefix + "public OctetsStream convert" + StringUtils.upper1(isKey ? "Key" : "Value")
				+ "(OctetsStream _os_) throws MarshalException {");
		if (sourceSchema.isDynamic() == targetSchema.isDynamic() && Schema.equals(sourceSchema, targetSchema)) {
			if (sourceSchema.isDynamic() && sourceSchema instanceof SchemaBean) {
				SchemaBean bean = (SchemaBean) sourceSchema;
				String className = bean.typeName();
				ps.println(prefix + "	" + className + " s = new " + className + "();");
				ps.println(prefix + "	s.unmarshal(_os_);");
				ps.println(prefix + "	return new OctetsStream().marshal(s);");
			} else
				ps.println(prefix + "	return _os_;");
		} else {
			String beanName = null;
			if (sourceSchema instanceof SchemaBean) {
				SchemaBean bean = (SchemaBean) sourceSchema;
				String className = bean.typeName();
				if (!sameBeans.contains(className))
					className = "SS." + className;
				ps.println(prefix + "	" + className + " s = new " + className + "();");
				ps.println(prefix + "	s.unmarshal(_os_);");
				beanName = bean.typeName();
			} else {
				Type type = isKey ? sourceMeta.getKeyType() : sourceMeta.getValueType();
				ConstructWithUnmarshal.make(type, "s", ps, prefix + "	");
			}
			if (targetSchema instanceof SchemaBean) {
				SchemaBean bean = (SchemaBean) targetSchema;
				String className = bean.typeName();
				if (!sameBeans.contains(className))
					className = "TT." + className;
				ps.println(prefix + "	" + className + " t = new " + className + "();");
				ps.println(prefix + (bean.typeName().equals(beanName) ? "	t.convertFrom(s);" : "	// TODO"));
				ps.println(prefix + "	return new OctetsStream().marshal(t);");
			} else {
				Type type = isKey ? targetMeta.getKeyType() : targetMeta.getValueType();
				Define.beginInitial(true);
				Define.make(type, "t", ps, prefix + "	");
				Define.endInitial();
				ps.println(prefix + "	// TODO");
				ps.println(prefix + "	_os_ = new OctetsStream();");
				Marshal.make(type, "t", ps, prefix + "	");
				ps.println(prefix + "	return _os_;");
			}
		}
		ps.println(prefix + "}");
		ps.println();
	}

	private static enum BeanOption {
		MARSHAL, UNMARSHAL
	}

	private void formatBean(Zdb sourceZdbMeta, String name, boolean needConvertFrom, Set<BeanOption> options,
			PrintStream ps, String prefix) {
		Main.isMakingConverter = true;
		ps.println(prefix + "static class " + name + " implements limax.codec.Marshal {");
		Xbean xb = sourceZdbMeta.getXbean(name);
		if (xb != null) {
			Collection<Variable> svars = xb.getStaticVariables();
			Collection<DynamicVariable> dvars = sourceZdbMeta.isDynamic()
					? targetZdbMeta.getXbean(name).getDynamicVariables() : null;
			Declare.make(xb.getEnums(), svars, Declare.Type.PUBLIC, ps, prefix + "    ");
			if (dvars != null)
				dvars.stream().filter(DynamicVariable::isMaster)
						.forEach(var -> Declare.make(var, Declare.Type.PUBLIC, ps, prefix + "    "));
			ps.println();
			ps.println(prefix + "	public " + xb.getLastName() + "() {");
			svars.forEach(var -> Construct.make(var, ps, prefix + "		"));
			if (dvars != null)
				dvars.stream().filter(DynamicVariable::isMaster)
						.forEach(var -> Construct.make(var, ps, prefix + "		"));
			ps.println(prefix + "	}");
			ps.println();
			Collection<Runnable> delayed;
			ps.println(prefix + "	@Override");
			ps.println(prefix + "	public OctetsStream marshal(OctetsStream _os_) {");
			if (options.contains(BeanOption.MARSHAL))
				delayed = Marshal.make(svars, dvars, ps, prefix + "    ");
			else {
				ps.println(prefix + "		throw new UnsupportedOperationException();");
				delayed = Collections.emptyList();
			}
			ps.println(prefix + "	}");
			ps.println();
			delayed.forEach(Runnable::run);
			ps.println(prefix + "	@Override");
			ps.println(prefix + "	public OctetsStream unmarshal(OctetsStream _os_) throws MarshalException {");
			if (options.contains(BeanOption.UNMARSHAL))
				delayed = Unmarshal.make(svars, dvars, xb.getName(), false, ps, prefix + "	");
			else {
				ps.println(prefix + "		throw new UnsupportedOperationException();");
				delayed = Collections.emptyList();
			}
			ps.println(prefix + "	}");
			ps.println();
			delayed.forEach(Runnable::run);
		} else {
			Cbean cb = sourceZdbMeta.getCbean(name);
			Declare.make(cb.getEnums(), cb.getVariables(), Declare.Type.PUBLIC, ps, prefix + "    ");
			Construct.make(cb, ps, prefix + "    ");
			if (options.contains(BeanOption.MARSHAL))
				Marshal.make(cb, ps, prefix + "    ");
			else {
				ps.println(prefix + "	public OctetsStream marshal(OctetsStream _os_) {");
				ps.println(prefix + "		throw new UnsupportedOperationException();");
				ps.println(prefix + "	}");
				ps.println();
			}
			if (options.contains(BeanOption.UNMARSHAL))
				Unmarshal.make(cb, ps, prefix + "    ");
			else {
				ps.println(prefix + "	public OctetsStream unmarshal(OctetsStream _os_) throws MarshalException {");
				ps.println(prefix + "		throw new UnsupportedOperationException();");
				ps.println(prefix + "	}");
				ps.println();
			}
		}
		if (needConvertFrom)
			formatBeanConvertFrom(name, ps, prefix + "	");
		ps.println(prefix + "}");
		ps.println();
	}

	private String formatFieldConvertFrom(Schema schema, String name, PrintStream ps, String prefix) {
		if (schema instanceof SchemaBean && !sameBeans.contains(((SchemaBean) schema).typeName())) {
			Define.make(((SchemaBean) schema).type(), "_t" + name, ps, prefix + "		");
			ps.println(prefix + "		_t" + name + ".convertFrom(" + name + ");");
			return "_t" + name;
		}
		return name;
	}

	private void formatBeanConvertFrom(String name, PrintStream ps, String prefix) {
		SchemaBean sourceBean = sourceBeans.get(name);
		SchemaBean targetBean = targetBeans.get(name);
		if (sourceBean == null || targetBean == null)
			return;
		Map<String, Schema> sourceEntries = new LinkedHashMap<>(sourceBean.entries());
		Map<String, Schema> targetEntries = new LinkedHashMap<>(targetBean.entries());
		targetEntries.putAll(targetBean.dynamicEntries());
		if (sourceBean.isDynamic())
			sourceEntries.putAll(targetBean.dynamicEntries());
		ps.println(prefix + "public void convertFrom(SS." + name + " _ss_) {");
		boolean hasTodo = false;
		for (Map.Entry<String, Schema> entry : targetEntries.entrySet()) {
			String fieldName = entry.getKey();
			Schema targetSchema = entry.getValue();
			Schema sourceSchema = sourceEntries.get(fieldName);
			if (sourceSchema == null) {
				ps.println(prefix + "	// TODO this." + fieldName + " = ");
				hasTodo = true;
			} else if (Schema.equals(sourceSchema, targetSchema)) {
				if (targetSchema instanceof SchemaCollection) {
					Schema elementSchema = ((SchemaCollection) targetSchema).elementSchema();
					if (elementSchema instanceof SchemaKeyValue) {
						SchemaKeyValue schema = (SchemaKeyValue) elementSchema;
						ps.println(prefix + "	_ss_." + fieldName + ".forEach((_k_, _v_) -> {");
						ps.println(prefix + "		this." + fieldName + ".put("
								+ formatFieldConvertFrom(schema.keySchema(), "_k_", ps, prefix) + ", "
								+ formatFieldConvertFrom(schema.valueSchema(), "_v_", ps, prefix) + ");");
					} else {
						ps.println(prefix + "	_ss_." + fieldName + ".forEach(_v_ -> {");
						ps.println(prefix + "		this." + fieldName + ".add("
								+ formatFieldConvertFrom(elementSchema, "_v_", ps, prefix) + ");");
					}
					ps.println(prefix + "	});");
				} else if (targetSchema instanceof SchemaBean
						&& !sameBeans.contains(((SchemaBean) targetSchema).typeName()))
					ps.println(prefix + "	this." + fieldName + ".convertFrom(_ss_." + fieldName + ");");
				else
					ps.println(prefix + "	this." + fieldName + " = _ss_." + fieldName + ";");
			} else {
				ps.println(prefix + "	// TODO this." + fieldName + " = _ss_." + fieldName + ";");
				hasTodo = true;
			}
		}
		if (!hasTodo)
			ps.println(prefix + "	// TODO");
		ps.println(prefix + "}");
		ps.println();
	}

	private static void findAllChildBean(Schema schema, Map<String, SchemaBean> collector) {
		if (schema instanceof SchemaBean) {
			SchemaBean sb = (SchemaBean) schema;
			if (collector.put(sb.typeName(), sb) == null)
				sb.entries().values().forEach(s -> findAllChildBean(s, collector));
		} else if (schema instanceof SchemaKeyValue) {
			findAllChildBean(((SchemaKeyValue) schema).keySchema(), collector);
			findAllChildBean(((SchemaKeyValue) schema).valueSchema(), collector);
		} else if (schema instanceof SchemaCollection) {
			findAllChildBean(((SchemaCollection) schema).elementSchema(), collector);
		}
	}
}
