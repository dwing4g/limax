package limax.util;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

import limax.util.ConcurrentEnvironment.ThreadPoolExecutorWrapper;

public class HashExecutor {
	private final ThreadPoolExecutorWrapper executor;
	private final SerialExecutor pool[];

	HashExecutor(ThreadPoolExecutorWrapper executor, int concurrencyLevel) {
		this.executor = executor;
		int capacity = 1;
		while (capacity < concurrencyLevel)
			capacity <<= 1;
		this.pool = new SerialExecutor[capacity];
		for (int i = 0; i < capacity; i++)
			this.pool[i] = new SerialExecutor();
	}

	public void execute(Runnable command) {
		if (executor.permit(command))
			executor.execute(command);
	}

	private static int hash(int h) {
		h ^= (h >>> 20) ^ (h >>> 12);
		return h ^ (h >>> 7) ^ (h >>> 4);
	}

	public Executor getExecutor(Object key) {
		return pool[hash(key == null ? 0 : key.hashCode()) & (pool.length - 1)];
	}

	public void execute(Object key, Runnable command) {
		if (executor.permit(command))
			getExecutor(key).execute(command);
	}

	private class SerialExecutor implements Executor {
		private final Queue<Runnable> queue = new ArrayDeque<Runnable>();
		private Runnable active;

		@Override
		public synchronized void execute(final Runnable r) {
			queue.offer(new Runnable() {
				@Override
				public void run() {
					try {
						r.run();
					} finally {
						synchronized (SerialExecutor.this) {
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
			if ((active = queue.poll()) != null)
				executor.execute(active);
			else
				executor.leave();
		}
	}
}
