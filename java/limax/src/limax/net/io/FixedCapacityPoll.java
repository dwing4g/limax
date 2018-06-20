package limax.net.io;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.PriorityBlockingQueue;

import limax.util.ConcurrentEnvironment;

public class FixedCapacityPoll extends PollPolicy {
	private final static String policyName = FixedCapacityPoll.class.getName();
	private final int capacity;

	public FixedCapacityPoll(int capacity) {
		super(new PriorityBlockingQueue<PollTask>(), ConcurrentEnvironment.getInstance().newThreadPool(policyName, 0));
		this.capacity = capacity;
	}

	@Override
	SelectionKey addChannel(SelectableChannel sc, int ops, Object att) throws IOException {
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

	@Override
	String getPolicyName() {
		return policyName;
	}
}
