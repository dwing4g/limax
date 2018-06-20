package limax.net.io;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Queue;
import java.util.concurrent.ExecutorService;

import limax.util.ConcurrentEnvironment;

public abstract class PollPolicy {
	final Queue<PollTask> q;
	final ExecutorService p;

	abstract SelectionKey addChannel(SelectableChannel sc, int ops, Object att) throws IOException;

	PollPolicy(Queue<PollTask> q, ExecutorService p) {
		this.p = p;
		this.q = q;
	}

	void schedule(PollTask task) {
		p.execute(task);
	}

	void cleanup() {
		for (PollTask task; (task = q.poll()) != null;)
			task.close();
		ConcurrentEnvironment.getInstance().shutdown(getPolicyName());
	}

	abstract String getPolicyName();
}