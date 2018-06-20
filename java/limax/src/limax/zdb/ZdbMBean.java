package limax.zdb;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public interface ZdbMBean {

	void backup(String path, boolean increment) throws IOException;

	long getDeadlockCount();

	long getTransactionCount();

	long getTransactionFalse();

	long getTransactionException();

	Map<String, AtomicInteger> top(String nsClass, String nsLock);
}
