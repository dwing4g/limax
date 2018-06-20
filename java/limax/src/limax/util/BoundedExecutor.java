package limax.util;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

public final class BoundedExecutor implements Executor {
	private final ThreadPoolExecutor executor;
	private final int maxOutstanding;
	private final int maxQueueCapacity;
	private final Queue<Runnable> queue = new ArrayDeque<>();
	private int outstanding = 0;
	private Runnable active;

	public BoundedExecutor(ThreadPoolExecutor executor, int maxOutstanding, int maxQueueCapacity) {
		if (maxOutstanding < 1)
			throw new IllegalArgumentException("maxOutstanding at least 1 but " + maxOutstanding);
		if (maxQueueCapacity < 0)
			throw new IllegalArgumentException("maxCapacity must nonnegative but " + maxQueueCapacity);
		this.executor = executor;
		this.maxOutstanding = maxOutstanding;
		this.maxQueueCapacity = maxQueueCapacity;
	}

	@Override
	public synchronized void execute(Runnable command) {
		if (outstanding == maxOutstanding && queue.size() == maxQueueCapacity)
			executor.getRejectedExecutionHandler().rejectedExecution(command, executor);
		queue.offer(() -> {
			try {
				command.run();
			} finally {
				synchronized (BoundedExecutor.this) {
					outstanding--;
					scheduleNext();
				}
			}
		});
		if (active == null)
			scheduleNext();
	}

	private void scheduleNext() {
		while ((active = queue.poll()) != null && outstanding++ < maxOutstanding)
			executor.execute(active);
	}
}