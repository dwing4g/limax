package limax.zdb;

public interface CheckpointMBean {

	int getCountCheckpoint();

	long getCountMarshalN();

	long getCountMarshal0();

	long getCountFlush();

	long getCountSnapshot();

	long getTotalTimeMarshalN();

	long getTotalTimeSnapshot();

	long getTotalTimeFlush();

	long getTotalTimeCheckpoint();

	String getTimeOfNextFlush();

	String getTimeOfNextCheckpoint();

	void checkpoint();

	int getPeriodCheckpoint();

}
