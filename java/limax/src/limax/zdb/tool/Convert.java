package limax.zdb.tool;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.xmlgen.Table;
import limax.xmlgen.Zdb;
import limax.zdb.DBC;

public class Convert {

	private Convert() {
	}

	public static Map<String, ConvertType> diff(Zdb sourceMeta, Zdb targetMeta, boolean includeSame,
			Set<String> unusedTable) {
		Map<String, ConvertType> res = new LinkedHashMap<>();
		sourceMeta.getTables().forEach(sourceTable -> {
			String tableName = sourceTable.getName();
			Table targetTable = targetMeta.getTable(tableName);
			if (targetTable != null) {
				Schema sourceSchema = Schemas.of(sourceTable);
				Schema targetSchema = Schemas.of(targetTable);
				ConvertType convertType = sourceSchema.isDynamic() == targetSchema.isDynamic()
						? sourceSchema.diff(targetSchema, false) : ConvertType.MANUAL;
				if (includeSame || convertType != ConvertType.SAME)
					res.put(tableName, convertType);
			} else if (unusedTable != null) {
				unusedTable.add(tableName);
			}
		});
		return res;
	}

	private static enum CType {
		SAME("copying..."), AUTO("auto converting..."), CONVERT("converting...");
		private final String status;

		private CType(String s) {
			status = s;
		}

		@Override
		public String toString() {
			return status;
		}
	}

	private static class AutoKeys {
		private final Map<String, AutoKey> map = new HashMap<>();

		private class AutoKey {
			private final String name;
			private final int initValue;
			private int step;
			private long current;

			AutoKey(OctetsStream os) throws MarshalException {
				this.name = os.unmarshal_String();
				this.initValue = os.unmarshal_int();
				this.step = os.unmarshal_int();
				this.current = os.unmarshal_long();
				map.put(this.name, this);
			}

			private OctetsStream marshal(OctetsStream os) {
				os.marshal(name);
				os.marshal(initValue);
				os.marshal(step);
				os.marshal(current);
				return os;
			}
		}

		public AutoKeys(Octets o) throws MarshalException {
			OctetsStream os = OctetsStream.wrap(o);
			for (int size = os.unmarshal_size(); size > 0; size--)
				new AutoKey(os);
		}

		public AutoKeys merge(AutoKeys source) {
			source.map.forEach((key, value) -> map.merge(key, value, (targetAutoKey, sourceAutoKey) -> {
				if (targetAutoKey.current < sourceAutoKey.current) {
					targetAutoKey.current = sourceAutoKey.current;
					targetAutoKey.step = sourceAutoKey.step;
				}
				return targetAutoKey;
			}));
			return this;
		}

		public Octets encode() {
			OctetsStream os = new OctetsStream().marshal_size(map.size());
			for (AutoKey autoKey : map.values())
				autoKey.marshal(os);
			return os;
		}
	}

	public static void convert(DBC sourceDBC, DBC targetDBC, Map<String, Converter> converterMap,
			Map<String, ConflictSolver> solverMap, boolean autoConvertWhenMaybeAuto, PrintStream trace) {

		if (trace == null)
			throw new NullPointerException();

		Map<String, CType> tables = new LinkedHashMap<>();
		diff(sourceDBC.meta(), targetDBC.meta(), true, null).forEach((tableName, convertType) -> {
			System.out.println(tableName + " " + convertType);
			Converter converter = (converterMap != null ? converterMap.get(tableName) : null);
			if (converter == null) {
				switch (convertType) {
				case SAME:
					tables.put(tableName, CType.SAME);
					break;
				case AUTO:
					tables.put(tableName, CType.AUTO);
					break;
				case MAYBE_AUTO:
					if (autoConvertWhenMaybeAuto)
						tables.put(tableName, CType.AUTO);
					else
						throw new RuntimeException(tableName + " MAYBE_AUTO, need converter");
					break;
				case MANUAL:
					throw new RuntimeException(tableName + " MANUAL, need converter");
				}
			} else
				tables.put(tableName, CType.CONVERT);
		});

		ExecutorService executor = Executors.newSingleThreadExecutor();

		{
			trace.println(CType.SAME + " _sys_");
			new limax.xmlgen.Table.Builder(sourceDBC.meta(), "_sys_", "", "");
			new limax.xmlgen.Table.Builder(targetDBC.meta(), "_sys_", "", "");
			DBC.Table sourceTable = sourceDBC.openTable("_sys_");
			DBC.Table targetTable = targetDBC.openTable("_sys_");
			sourceTable.walk((key, data) -> {
				executor.execute(() -> {
					try {
						Octets keyOs = Octets.wrap(key);
						Octets sourceValueOs = Octets.wrap(data);
						if (!targetTable.insert(keyOs, sourceValueOs))
							targetTable.replace(keyOs,
									new AutoKeys(targetTable.find(keyOs)).merge(new AutoKeys(sourceValueOs)).encode());
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				});
				return true;
			});
			sourceTable.close();
			targetTable.close();
		}

		tables.forEach((tableName, cType) -> {
			trace.println(cType + " " + tableName);

			DBC.Table sourceTable = sourceDBC.openTable(tableName);
			DBC.Table targetTable = targetDBC.openTable(tableName);
			SchemaKeyValue sourceSchema = Schemas.of(sourceTable.meta());
			SchemaKeyValue targetSchema = Schemas.of(targetTable.meta());

			Converter converter = (converterMap != null ? converterMap.get(tableName) : null);
			ConflictSolver conflictSolver = (solverMap != null ? solverMap.get(tableName) : null);

			sourceTable.walk((key, data) -> {
				executor.execute(() -> {
					try {
						OctetsStream keyOs = OctetsStream.wrap(Octets.wrap(key));
						OctetsStream sourceValueOs = OctetsStream.wrap(Octets.wrap(data));
						Data keyData = null;
						if (converter != null) {
							keyOs = converter.convertKey(keyOs);
							sourceValueOs = converter.convertValue(sourceValueOs);
						} else if (cType == CType.AUTO) {
							DataKeyValue sourceData = sourceSchema.create();
							sourceData.unmarshal(OctetsStream.wrap(new Octets(key).append(data)));
							DataKeyValue convertedData = targetSchema.create();
							sourceData.convertTo(convertedData);
							keyOs = new OctetsStream().marshal(convertedData.getKey());
							sourceValueOs = new OctetsStream().marshal(convertedData.getValue());
							keyData = convertedData.getKey();
						}
						if (!targetTable.insert(keyOs, sourceValueOs)) {
							if (keyData == null) {
								keyData = targetSchema.keySchema().create();
								keyData.unmarshal(keyOs);
								keyOs.position(0);
							}
							if (conflictSolver != null) {
								Octets targetValueOs = targetTable.find(keyOs);
								trace.println("    conflict key resolving... table=" + tableName + ", key=" + keyData);
								Octets solvedValueOs = conflictSolver.solve(sourceValueOs, targetValueOs, keyOs);
								targetTable.replace(keyOs, solvedValueOs);
							} else {
								throw new RuntimeException("insert failed! conflictSolver absent, table=" + tableName
										+ ", key=" + keyData);
							}
						}
					} catch (MarshalException e) {
						throw new RuntimeException(e);
					}
				}); // end executor
				return true;
			}); // end walk
			sourceTable.close();
			targetTable.close();
		}); // end tables

		for (executor.shutdown(); true;) {
			try {
				if (executor.awaitTermination(1, TimeUnit.SECONDS))
					break;
			} catch (InterruptedException ignore) {
			}
		}
	}

}
