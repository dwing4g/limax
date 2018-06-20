package limax.zdb;

public interface TTableMBean {
	String getName();

	String getLockName();

	String getPersistenceName();

	int getCacheCapacity();

	void setCacheCapacity(int capacity);

	int getCacheSize();

	String getCacheClassName();

	long getCountAdd();

	long getCountAddMiss();

	long getCountAddStorageMiss();

	long getCountGet();

	long getCountGetMiss();

	long getCountGetStorageMiss();

	long getCountRemove();

	long getCountRemoveMiss();

	long getCountRemoveStorageMiss();

	String getPercentAddHit();

	String getPercentGetHit();

	String getPercentRemoveHit();

	String getPercentCacheHit();

	long getStorageCountMarshal0();

	long getStorageCountMarshalN();

	long getStorageCountMarshalNTryFail();

	long getStorageCountFlush();

	long getStorageCountSnapshot();

	long getStorageFlushKeySize();

	long getStorageFlushValueSize();
}
