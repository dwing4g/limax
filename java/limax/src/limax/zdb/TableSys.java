package limax.zdb;

import limax.codec.Octets;
import limax.codec.OctetsStream;

final class TableSys extends AbstractTable {
	private StorageSys storage;

	TableSys() {
	}

	AutoKeys getAutoKeys() {
		return storage.autoKeys;
	}

	@Override
	public String getName() {
		return "_sys_";
	}

	@Override
	public Persistence getPersistence() {
		return Persistence.DB;
	}

	@Override
	StorageInterface open(limax.xmlgen.Table meta, LoggerEngine logger) {
		if (null != storage)
			throw new XError("TableSys has opened : " + getName());
		return storage = new StorageSys(logger);
	}

	@Override
	void close() {
		if (null != storage)
			storage = null;
	}

	private static class StorageSys implements StorageInterface {
		private final StorageEngine engine;
		private final OctetsStream keyOfAutoKeys;
		private final AutoKeys autoKeys;
		private OctetsStream snapshotValue = null;

		StorageSys(LoggerEngine logger) {
			switch (Zdb.meta().getEngineType()) {
			case MYSQL:
				engine = new StorageMysql((LoggerMysql) logger, "_sys_");
				break;
			case EDB:
				engine = new StorageEdb((LoggerEdb) logger, "_sys_");
				break;
			default:
				throw new XError("unknown engine type");
			}
			int autoKeyInitValue = Zdb.meta().getAutoKeyInitValue();
			int autoKeyStep = Zdb.meta().getAutoKeyStep();
			keyOfAutoKeys = new OctetsStream().marshal("limax.zdb.AutoKeys." + autoKeyInitValue);
			Octets value = engine.find(keyOfAutoKeys);
			autoKeys = new AutoKeys(value == null ? null : OctetsStream.wrap(value), autoKeyInitValue, autoKeyStep);
		}

		@Override
		public StorageEngine getEngine() {
			return engine;
		}

		@Override
		public long marshalN() {
			return 0;
		}

		@Override
		public long marshal0() {
			snapshotValue = autoKeys.encodeValue();
			return null != snapshotValue ? 1 : 0;
		}

		@Override
		public long snapshot() {
			return null != snapshotValue ? 1 : 0;
		}

		@Override
		public long flush0() {
			if (null == snapshotValue)
				return 0;
			engine.replace(keyOfAutoKeys, snapshotValue);
			return 1;
		}

		@Override
		public void cleanup() {
			snapshotValue = null;
		}
	}
}
