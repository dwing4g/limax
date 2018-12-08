package limax.util;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

import limax.util.ConcurrentEnvironment.ThreadPoolExecutorWrapper;

public class BoundedExecutor implements Executor {
	private final ThreadPoolExecutorWrapper executor;
	private final int concurrencyLevel;
	private final int maxQueueCapacity;
	private final Queue<Runnable> queue = new ArrayDeque<Runnable>();
	private int currency = 0;
	private Runnable active;

	BoundedExecutor(ThreadPoolExecutorWrapper executor, int concurrencyLevel, int maxQueueCapacity) {
		if (concurrencyLevel < 1)
			throw new IllegalArgumentException("maxOutstanding at least 1 but " + concurrencyLevel);
		if (maxQueueCapacity < 0)
			throw new IllegalArgumentException("maxCapacity must nonnegative but " + maxQueueCapacity);
		this.executor = executor;
		this.concurrencyLevel = concurrencyLevel;
		this.maxQueueCapacity = maxQueueCapacity;
	}

	@Override
	public synchronized void execute(final Runnable command) {
		if (!executor.permit(command))
			return;
		if (currency == concurrencyLevel && queue.size() == maxQueueCapacity)
			executor.reject(command);
		queue.offer(new Runnable() {
			public void run() {
				try {
					command.run();
				} finally {
					synchronized (BoundedExecutor.this) {
						currency--;
						scheduleNext();
					}
				}
			}
		});
		if (active == null) {
			executor.enter();
			scheduleNext();
		}
	}

	private void scheduleNext() {
		while ((active = queue.poll()) != null) {
			executor.execute(active);
			if (++currency == concurrencyLevel)
				return;
		}
		executor.leave();
	}
}