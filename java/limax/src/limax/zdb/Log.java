package limax.zdb;

interface Log {
	void commit();

	void rollback();
}
