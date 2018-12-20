package limax.zdb;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

final class Tables {
	private final Map<String, AbstractTable> tables = new HashMap<>();
	private final ReentrantReadWriteLock flushLock = new ReentrantReadWriteLock();
	private Path preload;
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
		if (!meta.getPreload().isEmpty())
			try {
				preload = Files.createDirectories(Paths.get(meta.getPreload()));
			} catch (Exception e) {
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
			ttable.preload(preload);
		}
		add(tableSys);
		if (preload != null)
			try (Stream<Path> stream = Files.list(preload)) {
				stream.forEach(path -> {
					try {
						Files.delete(path);
					} catch (Exception e) {
					}
				});
			} catch (Exception e) {
			}
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
