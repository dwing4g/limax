package limax.net.io;

import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;

class SSLServerTask extends SSLTask {
	private final AtomicInteger taskCounter;

	public SSLServerTask(ServerContextImpl context, NetProcessor processor, SSLContext sslContext) {
		super(context.getReadBufferSize(), context.getWriteBufferSize(), processor, sslContext);
		this.taskCounter = context.taskCounter;
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
