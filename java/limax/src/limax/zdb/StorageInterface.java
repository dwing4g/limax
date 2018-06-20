package limax.zdb;

interface StorageInterface {
	StorageEngine getEngine();

	int marshalN();

	int marshal0();

	int snapshot();

	int flush();
}
