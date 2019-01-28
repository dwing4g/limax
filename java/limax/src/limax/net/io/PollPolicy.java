package limax.net.io;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;

import limax.util.ConcurrentEnvironment;

public abstract class PollPolicy {
	volatile boolean closed;
	final Queue<PollTask> q;
	ExecutorService p;

	abstract SelectionKey addChannel(SelectableChannel sc, int ops, Object att) throws IOException;

	PollPolicy(Queue<PollTask> q) {
		this.q = q;
	}

	void schedule(PollTask task) {
		p.execute(task);
	}

	void shutdown() {
		closed = true;
		for (PollTask task : q)
			task.shutdown();
		for (PollTask task : q)
			task.awaitTermination();
		ConcurrentEnvironment.getInstance().shutdown(toString());
	}

	public static PollPolicy createFixedCapacityPool(int capacity) {
		return new FixedCapacityPoll(capacity);
	}

	public static PollPolicy createFixedCpuPoll(int ncpu) throws IOException {
		return new FixedCpuPoll(ncpu);
	}

	private static class FixedCpuPoll extends PollPolicy {
		FixedCpuPoll(int ncpu) throws IOException {
			super(new PriorityQueue<PollTask>(ncpu));
			p = ConcurrentEnvironment.getInstance().newFixedThreadPool(toString(), ncpu);
			for (int i = 0; i < ncpu; i++) {
				PollTask task = new PollTask();
				q.offer(task);
				schedule(task);
			}
		}

		@Override
		SelectionKey addChannel(SelectableChannel sc, int ops, Object att) throws IOException {
			if (closed)
				throw new ClosedSelectorException();
			synchronized (q) {
				PollTask task = q.poll();
				try {
					return task.register(sc, ops, att);
				} finally {
					q.offer(task);
				}
			}
		}
	}

	private static class FixedCapacityPoll extends PollPolicy {
		private final int capacity;

		FixedCapacityPoll(int capacity) {
			super(new PriorityBlockingQueue<PollTask>());
			p = ConcurrentEnvironment.getInstance().newThreadPool(toString(), 0);
			this.capacity = capacity;
		}

		@Override
		SelectionKey addChannel(SelectableChannel sc, int ops, Object att) throws IOException {
			if (closed)
				throw new ClosedSelectorException();
			PollTask task = q.poll();
			if (task == null) {
				schedule(task = new PollTask());
			} else if (task.size() >= capacity) {
				q.offer(task);
				schedule(task = new PollTask());
			}
			try {
				return task.register(sc, ops, att);
			} finally {
				q.offer(task);
			}
		}
	}

}