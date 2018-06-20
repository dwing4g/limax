package limax.zdb;

import java.io.IOException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import limax.util.ConcurrentEnvironment;
import limax.util.LockEnvironment;
import limax.util.MBeans;
import limax.util.Resource;
import limax.util.TimeoutExecutor;
import limax.util.Trace;

public final class Zdb implements ZdbMBean {
	private final static boolean useFixedThreadPool = Boolean.getBoolean("limax.zdb.Zdb.useFixedThreadPool");
	private final static int timeoutExecutorsMaxCacheSize = 64;
	private volatile limax.xmlgen.Zdb meta;
	private volatile Tables tables;
	private volatile Duration duration;
	private volatile Class<?> tableClass;
	private volatile Object table;
	private volatile LockEnvironment lockEnvironment;
	private volatile Checkpoint checkpoint;
	private volatile Resource mbeans;
	private volatile boolean isStartedOnce;
	private boolean running = false;

	private final static Zdb zdbinstance = new Zdb();

	public static Zdb getInstance() {
		return zdbinstance;
	}

	private Duration.Record _allocDurationRecord() {
		return duration != null ? duration.alloc() : null;
	}

	private Runnable _checkpointDuration() {
		return duration != null ? duration.checkpoint() : null;
	}

	static Duration.Record allocDurationRecord() {
		return zdbinstance._allocDurationRecord();
	}

	static Runnable checkpointDuration() {
		return zdbinstance._checkpointDuration();
	}

	static Tables tables() {
		return zdbinstance.tables;
	}

	static LockEnvironment lockEnvironment() {
		return zdbinstance.lockEnvironment;
	}

	static Random random() {
		return ThreadLocalRandom.current();
	}

	static Resource mbeans() {
		return zdbinstance.mbeans;
	}

	static limax.xmlgen.Zdb meta() {
		return zdbinstance.meta;
	}

	static limax.xmlgen.Procedure pmeta() {
		return zdbinstance.meta.getProcedure();
	}

	public static TimeoutExecutor executor() {
		return coreExecutor;
	}

	public static ScheduledExecutorService scheduler() {
		return scheduledExecutorService;
	}

	public static TimeoutExecutor procedureExecutor(long timeout) {
		if (timeout < 0)
			timeout = 0;
		TimeoutExecutor executor = procedureExecutors.get(timeout);
		if (executor == null) {
			executor = ConcurrentEnvironment.getInstance().newTimeoutExecutor("limax.zdb.procedure", timeout,
					TimeUnit.MILLISECONDS);
			if (procedureExecutors.size() >= timeoutExecutorsMaxCacheSize)
				procedureExecutors.clear();
			procedureExecutors.put(timeout, executor);
		}
		return executor;
	}

	private Zdb() {
		ManagementFactory.setZdbMBean(this);
	}

	private static ScheduledExecutorService scheduledExecutorService;
	private static TimeoutExecutor coreExecutor;
	private static Map<Long, TimeoutExecutor> procedureExecutors;

	private limax.xmlgen.Zdb testMeta(limax.xmlgen.Zdb meta) {
		switch (meta.getEngineType()) {
		case MYSQL:
			try (Connection conn = DriverManager.getConnection(meta.getDbHome())) {
			} catch (Exception e) {
				throw new XError("dbhome \"" + meta.getDbHome() + "\"", e);
			}
			break;
		case EDB:
			if (!Files.exists(Paths.get(meta.getDbHome())))
				throw new XError("dbhome \"" + meta.getDbHome() + "\" not exist");
			if (!Files.isDirectory(Paths.get(meta.getDbHome())))
				throw new XError("dbhome " + meta.getDbHome() + " not directory");
		}
		if (meta.getCheckpointPeriod() == 0)
			throw new XError("checkpointPeriod == 0");
		if (meta.getDeadlockDetectPeriod() == 0)
			throw new XError("deadlockDetectPeriod == 0");
		return meta;
	}

	public final synchronized void start(limax.xmlgen.Zdb meta) {
		if (DBC.isRunning())
			throw new IllegalAccessError("DBC is in use.");
		this.meta = testMeta(meta);
		Set<String> unusedTables = MetaUtils.testAndTrySaveToDbAndReturnUnusedTables(meta);
		if (!isStartedOnce) {
			Runtime.getRuntime().addShutdownHook(new Thread(Zdb.this::stop, "limax.zdb.ShutdownHook"));
			isStartedOnce = true;
		}
		Trace.fatal("zdb start begin");
		mbeans = MBeans.register(MBeans.root(), this, "limax.zdb:type=Zdb,name=Zdb");
		try {
			ConcurrentEnvironment env = ConcurrentEnvironment.getInstance();
			if (useFixedThreadPool) {
				env.newFixedThreadPool("limax.zdb.core", meta.getCorePoolSize());
				env.newFixedThreadPool("limax.zdb.procedure", meta.getProcPoolSize());
			} else {
				env.newThreadPool("limax.zdb.core", meta.getCorePoolSize());
				env.newThreadPool("limax.zdb.procedure", meta.getProcPoolSize());
			}
			scheduledExecutorService = env.newScheduledThreadPool("limax.zdb.scheduler", meta.getSchedPoolSize());
			coreExecutor = env.newTimeoutExecutor("limax.zdb.core", meta.getProcedure().getMaxExecutionTime(),
					TimeUnit.MILLISECONDS);
			lockEnvironment = new LockEnvironment(() -> meta.getDeadlockDetectPeriod());
			ScheduledThreadPoolExecutor checkpointScheduler = env.newScheduledThreadPool("limax.zdb.checkpoint", 1);
			procedureExecutors = new ConcurrentHashMap<>();
			Class.forName("limax.zdb.Lockeys");
			Class.forName("limax.zdb.XBean");
			tableClass = Class.forName("table._Tables_");
			Constructor<?> constructor = tableClass.getDeclaredConstructor();
			constructor.setAccessible(true);
			table = constructor.newInstance();
			tables = new Tables();
			Field[] fields = tableClass.getDeclaredFields();
			AccessibleObject.setAccessible(fields, true);
			for (Field field : fields) {
				Object fieldValue = field.get(table);
				if (fieldValue instanceof AbstractTable)
					tables.add((AbstractTable) fieldValue);
			}
			tables.open(meta, unusedTables);
			tables.getLogger().dropTables(unusedTables.toArray(new String[unusedTables.size()]));
			checkpoint = new Checkpoint(checkpointScheduler, tables);
			String trnHome = meta.getTrnHome();
			if (!trnHome.isEmpty()) {
				Path path = Paths.get(trnHome);
				Files.createDirectories(path);
				duration = new Duration(path, tables, () -> checkpoint.checkpoint(meta));
			}
			ManagementFactory.setCheckpointMBean(checkpoint);
			Trace.fatal("zdb start end");
			running = true;
		} catch (Throwable e) {
			Trace.fatal("Zdb start error", e);
			close();
			throw e instanceof XError ? (XError) e : new XError(e);
		}
	}

	private synchronized void close() {
		Trace.fatal("zdb stop begin");
		ConcurrentEnvironment.getInstance().shutdown("limax.zdb.procedure", "limax.zdb.scheduler", "limax.zdb.core",
				"limax.zdb.checkpoint");
		if (null != checkpoint) {
			checkpoint.cleanup();
			checkpoint = null;
		}
		ManagementFactory.setCheckpointMBean(null);
		if (null != duration) {
			try {
				duration.close();
			} catch (IOException e) {
			}
			duration = null;
		}
		try {
			Field field = tableClass.getDeclaredField("instance");
			field.setAccessible(true);
			field.set(table, null);
		} catch (Exception ignored) {
		}
		if (null != tables) {
			tables.close();
			tables = null;
		}
		mbeans.close();
		Trace.fatal("zdb stop end");
		running = false;
	}

	public final synchronized void stop() {
		if (running)
			close();
	}

	@Override
	public long getDeadlockCount() {
		LockEnvironment le = lockEnvironment;
		return le == null ? 0 : le.getDeadlockCount();
	}

	@Override
	public long getTransactionCount() {
		return Transaction.getTotalCount();
	}

	@Override
	public long getTransactionFalse() {
		return Transaction.getTotalFalse();
	}

	@Override
	public long getTransactionException() {
		return Transaction.getTotalException();
	}

	@Override
	public Map<String, AtomicInteger> top(String nsClass, String nsLock) {
		return new StackTrace(nsClass, nsLock).top();
	}

	@Override
	public void backup(String path, boolean increment) throws IOException {
		if (meta.getEngineType() == limax.xmlgen.Zdb.EngineType.EDB) {
			tables.getLogger().backup(path, increment);
			Files.copy(Paths.get(meta.getDbHome(), "meta.xml"), Paths.get(path).resolve("meta.xml"),
					StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
		}
	}

}
