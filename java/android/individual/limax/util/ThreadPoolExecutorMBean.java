package limax.util;

import java.util.concurrent.ThreadPoolExecutor;

class ThreadPoolExecutorMBean {
	private final ThreadPoolExecutor executor;

	ThreadPoolExecutorMBean(String name, ThreadPoolExecutor executor) {
		this.executor = executor;
	}

	ThreadPoolExecutor getThreadPoolExecutor() {
		return executor;
	}

	void close() {
	}

}
