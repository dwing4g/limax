package limax.net.io;

import java.util.concurrent.atomic.AtomicInteger;

class PollServerTask extends PollNetTask {
	private final AtomicInteger taskCounter;

	PollServerTask(AbstractServerContext context, NetProcessor processor) {
		super(context.getRecvBufferSize(), context.getSendBufferSize(), context.createSSLExchange(), processor);
		(this.taskCounter = context.taskCounter).incrementAndGet();
	}

	@Override
	void onClose() {
		taskCounter.decrementAndGet();
		super.onClose();
	}
}