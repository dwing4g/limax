package limax.xmlgen;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;

import org.w3c.dom.Element;

import limax.util.ElementHelper;
import limax.util.Trace;
import limax.util.XMLUtils;

public class Zdb extends Naming {
	public enum EngineType {
		MYSQL, EDB
	}

	private boolean dynamic = false;
	private String dbhome;
	private String trnhome;
	private String preload;

	private String defaultTableCache = "limax.zdb.TTableCacheLRU";
	private boolean zdbVerify = true;

	private int autoKeyInitValue = 0;
	private int autoKeyStep = 4096;

	private int corePoolSize = 30;
	private int procPoolSize = 10;
	private int schedPoolSize = 5;

	private int checkpointPeriod = 60000;
	private int deadlockDetectPeriod = 1000;
	private long snapshotFatalTime = 200;
	private int marshalPeriod = -1;
	private int marshalN = 1;

	private int jdbcPoolSize = 5;
	private int edbCacheSize = 65536;
	private int edbLoggerPages = 16384;

	public static Zdb loadFromClass() throws Exception {
		Method method = Class.forName("table._Meta_").getMethod("create");
		method.setAccessible(true);
		Zdb zdb = (Zdb) method.invoke(null);
		compile(zdb);
		return zdb;
	}

	public static EngineType getEngineType(String dbhome) {
		if (dbhome.startsWith("jdbc:mysql"))
			return EngineType.MYSQL;
		Paths.get(dbhome);
		return EngineType.EDB;
	}

	public EngineType getEngineType() {
		return getEngineType(dbhome);
	}

	public static Zdb loadFromDb(String dbhome) throws Exception {
		Zdb zdb = null;
		switch (getEngineType(dbhome)) {
		case MYSQL:
			try (Connection conn = DriverManager.getConnection(dbhome)) {
				try (Statement st = conn.createStatement();
						ResultSet rs = st.executeQuery("SELECT value FROM _meta_ WHERE id=0")) {
					if (!rs.next())
						return null;
					zdb = new Zdb(new Naming.Root(), XMLUtils.getRootElement(rs.getBlob(1).getBinaryStream()));
				} catch (Exception e) {
					return null;
				}
			}
			break;
		case EDB:
			File file = new File(dbhome, "meta.xml");
			if (!file.exists())
				return null;
			zdb = new Zdb(new Naming.Root(), XMLUtils.getRootElement(file));
			break;
		}
		zdb.dbhome = dbhome;
		compile(zdb);
		return zdb;
	}

	private static void compile(Zdb zdb) throws Exception {
		Collection<Naming> unresoloved = zdb.getRoot().compile();
		if (!unresoloved.isEmpty()) {
			Trace.error(" Unresolved symobls:");
			unresoloved.forEach(Trace::error);
			throw new Exception("has unresolved symobls");
		}
	}

	private Zdb(Root root, Element self) throws Exception {
		super(root, self);
		initialize(self);
	}

	public Zdb(Project parent, Element self) throws Exception {
		super(parent, self);
		initialize(self);
	}

	public void initialize(Element self) {
		ElementHelper eh = new ElementHelper(self);
		dynamic = eh.getBoolean("dynamic", false);
		dbhome = eh.getString("dbhome", "zdb");
		trnhome = eh.getString("trnhome");
		preload = eh.getString("preload");
		jdbcPoolSize = eh.getInt("jdbcPoolSize", jdbcPoolSize);
		defaultTableCache = eh.getString("defaultTableCache", defaultTableCache);
		zdbVerify = eh.getBoolean("zdbVerify", true);
		autoKeyInitValue = eh.getInt("autoKeyInitValue", autoKeyInitValue);
		autoKeyStep = eh.getInt("autoKeyStep", autoKeyStep);
		corePoolSize = eh.getInt("corePoolSize", corePoolSize);
		procPoolSize = eh.getInt("procPoolSize", procPoolSize);
		schedPoolSize = eh.getInt("schedPoolSize", schedPoolSize);
		checkpointPeriod = eh.getInt("checkpointPeriod", checkpointPeriod);
		deadlockDetectPeriod = eh.getInt("deadlockDetectPeriod", deadlockDetectPeriod);
		snapshotFatalTime = eh.getLong("snapshotFatalTime", snapshotFatalTime);
		marshalPeriod = eh.getInt("marshalPeriod", marshalPeriod);
		marshalN = eh.getInt("marshalN", marshalN);
		edbCacheSize = eh.getInt("edbCacheSize", edbCacheSize);
		edbLoggerPages = eh.getInt("edbLoggerPages", edbLoggerPages);
		eh.warnUnused("xml:base", "xmlns:xi", "name");
	}

	public static final class Builder {
		Zdb zdb;

		public Builder(Root root) {
			zdb = new Zdb(root);
		}

		public Zdb build() {
			return zdb;
		}

		public Builder dynamic() {
			zdb.dynamic = true;
			return this;
		}

		public Builder defaultTableCache(String defaultTableCache) {
			zdb.defaultTableCache = defaultTableCache;
			return this;
		}

		public Builder zdbVerify(boolean zdbVerify) {
			zdb.zdbVerify = zdbVerify;
			return this;
		}

		public Builder autoKeyInitValue(int autoKeyInitValue) {
			zdb.autoKeyInitValue = autoKeyInitValue;
			return this;
		}

		public Builder autoKeyStep(int autoKeyStep) {
			zdb.autoKeyStep = autoKeyStep;
			return this;
		}

		public Builder corePoolSize(int corePoolSize) {
			zdb.corePoolSize = corePoolSize;
			return this;
		}

		public Builder procPoolSize(int procPoolSize) {
			zdb.procPoolSize = procPoolSize;
			return this;
		}

		public Builder schedPoolSize(int schedPoolSize) {
			zdb.schedPoolSize = schedPoolSize;
			return this;
		}

		public Builder checkpointPeriod(int checkpointPeriod) {
			zdb.checkpointPeriod = checkpointPeriod;
			return this;
		}

		public Builder deadlockDetectPeriod(int deadlockDetectPeriod) {
			zdb.deadlockDetectPeriod = deadlockDetectPeriod;
			return this;
		}

		public Builder snapshotFatalTime(int snapshotFatalTime) {
			zdb.snapshotFatalTime = snapshotFatalTime;
			return this;
		}

		public Builder marshalPeriod(int marshalPeriod) {
			zdb.marshalPeriod = marshalPeriod;
			return this;
		}

		public Builder marshalN(int marshalN) {
			zdb.marshalN = marshalN;
			return this;
		}

		public Builder edbCacheSize(int edbCacheSize) {
			zdb.edbCacheSize = edbCacheSize;
			return this;
		}

		public Builder edbLoggerPages(int edbLoggerPages) {
			zdb.edbLoggerPages = edbLoggerPages;
			return this;
		}
	}

	private Zdb(Root root) {
		super(root, "");
	}

	@Override
	public boolean resolve() {
		if (!super.resolve())
			return false;
		List<Procedure> procedures = getChildren(Procedure.class);
		if (procedures.size() > 1) {
			throw new RuntimeException("procedure.size > 1");
		} else if (procedures.isEmpty()) {
			new Procedure(this).resolved();
		}
		return true;
	}

	public Procedure getProcedure() {
		return getChildren(Procedure.class).get(0);
	}

	public List<Variable> getDescendantVariables() {
		return getDescendants(Variable.class);
	}

	public List<Xbean> getXbeans() {
		return getChildren(Xbean.class);
	}

	public List<Cbean> getCbeans() {
		return getChildren(Cbean.class);
	}

	public List<Table> getTables() {
		return getChildren(Table.class);
	}

	public Table getTable(String name) {
		return getChild(Table.class, name);
	}

	public Cbean getCbean(String name) {
		return getChild(Cbean.class, name);
	}

	public Xbean getXbean(String name) {
		return getChild(Xbean.class, name);
	}

	public int getJdbcPoolSize() {
		return jdbcPoolSize;
	}

	public boolean isDynamic() {
		return dynamic;
	}

	public String getDbHome() {
		return dbhome;
	}

	public String getTrnHome() {
		return trnhome == null ? "" : trnhome;
	}

	public String getPreload() {
		return preload == null ? "" : preload;
	}

	public int getAutoKeyInitValue() {
		return autoKeyInitValue;
	}

	public int getAutoKeyStep() {
		return autoKeyStep;
	}

	public boolean isZdbVerify() {
		return zdbVerify;
	}

	public int getSchedPoolSize() {
		return schedPoolSize;
	}

	public int getCorePoolSize() {
		return corePoolSize;
	}

	public int getProcPoolSize() {
		return procPoolSize;
	}

	public int getDeadlockDetectPeriod() {
		return deadlockDetectPeriod;
	}

	public long getSnapshotFatalTime() {
		return snapshotFatalTime;
	}

	public int getMarshalN() {
		return marshalN;
	}

	public int getMarshalPeriod() {
		return marshalPeriod;
	}

	public int getCheckpointPeriod() {
		return checkpointPeriod;
	}

	public String getDefaultTableCache() {
		return defaultTableCache;
	}

	public int getEdbCacheSize() {
		return edbCacheSize;
	}

	public int getEdbLoggerPages() {
		return edbLoggerPages;
	}

	public void setDbHome(String dbhome) {
		this.dbhome = dbhome;
	}
}
