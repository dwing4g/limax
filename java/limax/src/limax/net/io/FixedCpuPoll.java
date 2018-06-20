package limax.net.io;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.PriorityQueue;

import limax.util.ConcurrentEnvironment;

public class FixedCpuPoll extends PollPolicy {
	private final static String policyName = FixedCpuPoll.class.getName();

	public FixedCpuPoll(int ncpu) throws IOException {
		super(new PriorityQueue<PollTask>(ncpu),
				ConcurrentEnvironment.getInstance().newFixedThreadPool(policyName, ncpu));
		for (int i = 0; i < ncpu; i++) {
			PollTask task = new PollTask();
			q.offer(task);
			schedule(task);
		}
	}

	@Override
	SelectionKey addChannel(SelectableChannel sc, int ops, Object att) throws IOException {
		synchronized (q) {
			PollTask task = q.poll();
			try {
				return task.register(sc, ops, att);
			} finally {
				q.offer(task);
			}
		}
	}

	@Override
	String getPolicyName() {
		return policyName;
	}
}