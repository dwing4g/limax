package limax.util;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public final class Dispatcher {
	private final AtomicInteger running = new AtomicInteger();
	private final HashExecutor hashExecutor;
	private final Executor executor;
	private volatile Thread shutdownThread;

	public Dispatcher(HashExecutor hashExecutor) {
		this.hashExecutor = hashExecutor;
		this.executor = null;
	}

	public Dispatcher(Executor executor) {
		this.hashExecutor = null;
		this.executor = executor;
	}

	public void execute(final Runnable r, Object hit) {
		running.incrementAndGet();
		Runnable wrapper = new Runnable() {
			@Override
			public void run() {
				try {
					r.run();
				} finally {
					if (running.decrementAndGet() == 0)
						LockSupport.unpark(shutdownThread);
				}
			}
		};
		if (hashExecutor != null)
			hashExecutor.execute(hit, wrapper);
		else
			executor.execute(wrapper);
	}

	public void await() {
		synchronized (running) {
			shutdownThread = Thread.currentThread();
			while (running.get() != 0)
				LockSupport.park(running);
			shutdownThread = null;
		}
	}

	public interface Dispatchable {
		void run() throws Throwable;
	}

	private static class WrappedDispatchable implements Runnable {
		private final Dispatchable r;
		private Throwable throwable = null;
		private boolean done = false;

		public WrappedDispatchable(Dispatchable r) {
			this.r = r;
		}

		@Override
		public synchronized void run() {
			try {
				r.run();
			} catch (Throwable t) {
				throwable = t;
			} finally {
				done = true;
				notify();
			}
		}
	}

	public Throwable run(Dispatchable r) {
		WrappedDispatchable wrapper = new WrappedDispatchable(r);
		synchronized (wrapper) {
			if (hashExecutor != null)
				hashExecutor.execute(wrapper);
			else
				executor.execute(wrapper);
			while (!wrapper.done)
				try {
					wrapper.wait();
				} catch (InterruptedException e) {
				}
		}
		return wrapper.throwable;
	}
}
