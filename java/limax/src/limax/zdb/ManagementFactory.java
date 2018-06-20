package limax.zdb;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ManagementFactory {

	private ManagementFactory() {
	}

	private static volatile CheckpointMBean checkpoint;
	private static volatile ZdbMBean zdb;
	private static final Map<String, TTableMBean> tables = new ConcurrentHashMap<>();

	static void setCheckpointMBean(CheckpointMBean checkpoint) {
		ManagementFactory.checkpoint = checkpoint;
	}

	public static CheckpointMBean getCheckpointMBean() {
		return checkpoint;
	}

	public static ZdbMBean getZdbMBean() {
		return zdb;
	}

	static void setZdbMBean(ZdbMBean zdb) {
		ManagementFactory.zdb = zdb;
	}

	static void setTTableMBean(String name, TTableMBean table) {
		tables.put(name, table);
	}

	public static TTableMBean getTTableMBean(String name) {
		return tables.get(name);
	}

	public static Map<String, TTableMBean> getTTableMBeans() {
		return Collections.unmodifiableMap(tables);
	}

}
