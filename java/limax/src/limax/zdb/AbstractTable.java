package limax.zdb;

abstract class AbstractTable implements Table {
	abstract StorageInterface open(limax.xmlgen.Table meta, LoggerEngine logger);

	abstract void close();
}
