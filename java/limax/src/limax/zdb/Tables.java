package limax.zdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

final class Tables {
	private final Map<String, AbstractTable> tables = new HashMap<>();
	private final ReentrantReadWriteLock flushLock = new ReentrantReadWriteLock();
	private LoggerEngine logger;

	private List<StorageInterface> storages = new ArrayList<>();
	private TableSys tableSys = new TableSys();

	TableSys getTableSys() {
		return tableSys;
	}

	void add(AbstractTable table) {
		if (null != tables.put(table.getName(), table))
			throw new XError("duplicate table name " + table.getName());
	}

	void open(limax.xmlgen.Zdb meta, Set<String> unusedTables) {
		if (null != logger)
			throw new XError("tables opened");
		switch (meta.getEngineType()) {
		case MYSQL:
			logger = new LoggerMysql(meta);
			break;
		case EDB:
			logger = new LoggerEdb(meta);
			break;
		}
		storages.add(tableSys.open(null, logger));
		tableSys.getAutoKeys().remove(unusedTables);
		Map<String, Integer> map = new HashMap<>();
		AtomicInteger idAlloc = new AtomicInteger();
		for (AbstractTable table : tables.values()) {
			StorageInterface storage = table.open(meta.getTable(table.getName()), logger);
			if (storage != null)
				storages.add(storage);
			TTable<?, ?> ttable = (TTable<?, ?>) table;
			ttable.setLockId(map.computeIfAbsent(ttable.getLockName(), k -> idAlloc.incrementAndGet()));
		}
		add(tableSys);
	}

	final void close() {
		storages.clear();
		tables.values().forEach(table -> table.close());
		if (null != logger) {
			logger.close();
			logger = null;
		}
	}

	Lock flushReadLock() {
		return flushLock.readLock();
	}

	boolean isFlushWriteLockHeldByCurrentThread() {
		return flushLock.isWriteLockedByCurrentThread();
	}

	Lock flushWriteLock() {
		return flushLock.writeLock();
	}

	List<StorageInterface> getStorages() {
		return storages;
	}

	LoggerEngine getLogger() {
		return logger;
	}

	TTable<?, ?> getTable(String table) {
		return (TTable<?, ?>) tables.get(table);
	}
}
