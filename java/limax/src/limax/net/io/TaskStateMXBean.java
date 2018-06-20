package limax.net.io;

import java.util.List;

public interface TaskStateMXBean {
	long getTotal();

	long getRunning();

	List<Integer> getPollTaskSize();
}