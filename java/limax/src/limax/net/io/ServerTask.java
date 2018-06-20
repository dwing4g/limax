package limax.net.io;

import java.util.concurrent.atomic.AtomicInteger;

class ServerTask extends NetTaskImpl {
	private final AtomicInteger taskCounter;

	public ServerTask(ServerContextImpl context, NetProcessor processor) {
		super(context.getReadBufferSize(), context.getWriteBufferSize(), processor);
		this.taskCounter = context.taskCounter;
	}

	@Override
	protected void onAbort() {
	}

	@Override
	protected void onBindKey() throws Exception {
		super.onBindKey();
		taskCounter.incrementAndGet();
	}

	@Override
	protected void onUnbindKey() {
		taskCounter.decrementAndGet();
	}
}