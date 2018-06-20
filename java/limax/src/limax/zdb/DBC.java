package limax.zdb;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import limax.codec.Octets;
import limax.util.Trace;

public class DBC {

	private final static class Manager {
		private final ReentrantLock lock = new ReentrantLock();
		private Map<String, DBC> dbs;

		Manager() {
			Runtime.getRuntime().addShutdownHook(new Thread("limax.zdb.DBC.ShutdownHook") {
				@Override
				public void run() {
					Manager.this.stop();
				}
			});
		}

		public boolean isRunning() {
			lock.lock();
			try {
				return dbs != null;
			} finally {
				lock.unlock();
			}
		}

		public boolean start() {
			Trace.openIf();
			lock.lock();
			try {
				if (dbs != null)
					return false;
				Trace.info("limax.zdb.DBC start ...");
				dbs = new HashMap<>();
				return true;
			} finally {
				lock.unlock();
			}
		}

		public void stop() {
			lock.lock();
			try {
				if (dbs != null) {
					Trace.info("limax.zdb.DBC stop begin");
					for (DBC db : getDbs())
						db.close();
					dbs = null;
					Trace.info("limax.zdb.DBC stop end");
				}
			} finally {
				lock.unlock();
			}
		}

		public DBC open(limax.xmlgen.Zdb meta) {
			String key = meta.getDbHome();
			lock.lock();
			try {
				DBC dbc = dbs.get(key);
				if (dbc == null)
					dbs.put(key, dbc = new DBC(meta));
				return dbc;
			} finally {
				lock.unlock();
			}
		}

		public DBC[] getDbs() {
			lock.lock();
			try {
				return dbs != null ? dbs.values().toArray(new DBC[dbs.size()]) : new DBC[0];
			} finally {
				lock.unlock();
			}
		}

		private void removeDb(DBC db) {
			lock.lock();
			try {
				dbs.remove(db.meta.getDbHome());
			} finally {
				lock.unlock();
			}
		}

	}

	private static final Manager manager = new Manager();

	public static boolean isRunning() {
		return manager.isRunning();
	}

	public static boolean start() {
		return manager.start();
	}

	public static void stop() {
		manager.stop();
	}

	public static DBC open(limax.xmlgen.Zdb meta) {
		if (!manager.isRunning())
			throw new IllegalStateException("DBC is stopped");
		return manager.open(meta);
	}

	private final ReentrantLock dbLock = new ReentrantLock();
	private final Map<String, Table> tables = new HashMap<>();

	private volatile LoggerEngine logger;
	private final limax.xmlgen.Zdb meta;
	private final static int COMMIT_BATCH = 1000;

	public class Table {
		private final String name;
		private final limax.xmlgen.Table meta; // fast reference
		private StorageEngine engine;
		private int count = 0;

		Table(String tableName) {
			switch (getDatabase().meta().getEngineType()) {
			case MYSQL:
				engine = new StorageMysql((LoggerMysql) logger, tableName);
				break;
			case EDB:
				engine = new StorageEdb((LoggerEdb) logger, tableName);
			}
			name = tableName;
			meta = DBC.this.meta().getTable(tableName);
		}

		private void checkpoint(boolean force) {
			if (force || ++count == COMMIT_BATCH) {
				count = 0;
				logger.checkpoint();
			}
		}

		public DBC getDatabase() {
			return DBC.this;
		}

		public limax.xmlgen.Table meta() {
			return meta;
		}

		public String getName() {
			return name;
		}

		public void close() {
			checkpoint(true);
			DBC.this.removeTable(getName());
		}

		public Octets find(Octets key) {
			return engine.find(key);
		}

		public void replace(Octets key, Octets value) {
			engine.replace(key, value);
			checkpoint(false);
		}

		public boolean insert(Octets key, Octets value) {
			boolean r = engine.insert(key, value);
			checkpoint(false);
			return r;
		}

		public void remove(Octets key) {
			engine.remove(key);
			checkpoint(false);
		}

		public void walk(IWalk iw) {
			engine.walk(iw);
		}
	}

	private DBC(limax.xmlgen.Zdb meta) {
		this.meta = meta;
		MetaUtils.testAndTrySaveToDbAndReturnUnusedTables(meta);
		switch (meta.getEngineType()) {
		case MYSQL:
			logger = new LoggerMysql(meta);
			break;
		case EDB:
			logger = new LoggerEdb(meta);
		}
	}

	public limax.xmlgen.Zdb meta() {
		return meta;
	}

	public void close() {
		dbLock.lock();
		try {
			if (logger == null)
				return;
			for (Table table : getTables())
				table.close();
			manager.removeDb(this);
			logger.close();
			logger = null;
		} finally {
			dbLock.unlock();
		}
	}

	public boolean isClosed() {
		dbLock.lock();
		try {
			return logger == null;
		} finally {
			dbLock.unlock();
		}
	}

	public Table[] getTables() {
		dbLock.lock();
		try {
			return tables.values().toArray(new Table[tables.size()]);
		} finally {
			dbLock.unlock();
		}
	}

	public Table getTable(String tableName) {
		dbLock.lock();
		try {
			return tables.get(tableName);
		} finally {
			dbLock.unlock();
		}
	}

	private Table removeTable(String tableName) {
		dbLock.lock();
		try {
			return tables.remove(tableName);
		} finally {
			dbLock.unlock();
		}
	}

	public Table openTable(String tableName) {
		if (null == meta.getTable(tableName))
			throw new IllegalStateException("table not in meta: " + tableName);
		dbLock.lock();
		try {
			if (logger == null)
				throw new IllegalStateException("Database has closed.");
			Table table = tables.get(tableName);
			if (null == table)
				tables.put(tableName, table = new Table(tableName));
			return table;
		} finally {
			dbLock.unlock();
		}
	}

	public void dropTable(String tableName) throws Exception {
		if (null == meta.getTable(tableName))
			throw new IllegalStateException("table not in meta: " + tableName);
		dbLock.lock();
		try {
			if (logger == null)
				throw new IllegalStateException("Database has closed.");
			tables.remove(tableName);
			logger.dropTables(new String[] { tableName });
		} finally {
			dbLock.unlock();
		}
	}
}
