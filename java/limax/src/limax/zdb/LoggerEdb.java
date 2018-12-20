package limax.zdb;

import java.io.IOException;
import java.nio.file.Paths;

import limax.edb.DataBase;
import limax.edb.Environment;

class LoggerEdb implements LoggerEngine {
	private DataBase database;
	private final Environment env = new Environment();

	LoggerEdb(limax.xmlgen.Zdb meta) {
		initialize(meta.getDbHome(), meta.getEdbCacheSize(), meta.getEdbLoggerPages());
	}

	private void initialize(String dbhome, int edbCacheSize, int edbLoggerPages) {
		try {
			env.setCacheSize(edbCacheSize);
			env.setLoggerPages(edbLoggerPages);
			env.setCheckpointPeriod(0);
			env.setCheckpointOnCacheFull(false);
			database = new DataBase(env, Paths.get(dbhome));
		} catch (Exception e) {
			throw new XError(e);
		}
	}

	DataBase getDatabase() {
		return database;
	}

	@Override
	public void checkpoint() {
		try {
			database.checkpoint();
		} catch (Exception e) {
			throw new XError(e);
		}
	}

	@Override
	public void close() {
		try {
			database.close();
		} catch (Exception e) {
			throw new XError(e);
		}
	}

	@Override
	public void backup(String path, boolean increment) throws IOException {
		env.backup(path, increment);
	}

	@Override
	public void dropTables(String[] tableNames) throws Exception {
		database.removeTable(tableNames);
	}
}
