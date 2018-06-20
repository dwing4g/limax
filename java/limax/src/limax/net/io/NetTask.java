package limax.net.io;

public interface NetTask {
	void send(byte[] data, int off, int len);

	void send(byte[] data);

	void sendFinal();

	void suspend(long millisecond, final Runnable finishSuspend);

	boolean hasReadBufferRemain();

	long getSendBufferSize();

	void enableRead();

	void enableWrite();

	void disableRead();

	void disableWrite();

	void enableReadWrite();

	void disableReadWrite();

	void schedule(Runnable r);

	void resetAlarm(long millisecond);
}
