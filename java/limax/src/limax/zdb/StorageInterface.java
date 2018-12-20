package limax.zdb;

interface StorageInterface {
	StorageEngine getEngine();

	long marshalN();

	long marshal0();

	long snapshot();

	long flush0();

	void cleanup();

	default long flush1() {
		long count = flush0();
		cleanup();
		return count;
	}
}
