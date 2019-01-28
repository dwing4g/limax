package limax.net.io;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.Channel;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;

import limax.util.MBeans;

abstract class AbstractServerContext implements ServerContextMXBean {
	protected final SocketAddress sa;
	protected final NetTaskConstructor constructor;
	final AtomicInteger taskCounter = new AtomicInteger();
	protected volatile int backlog;
	private volatile int rsize;
	private volatile int wsize;
	private final SSLContext sslContext;
	private final int sslMode;
	protected Channel channel;

	@Override
	public synchronized void close() throws IOException {
		if (channel != null) {
			try {
				channel.close();
			} finally {
				channel = null;
			}
		}
	}

	AbstractServerContext(SocketAddress sa, int backlog, int rsize, int wsize, SSLContext sslContext, int sslMode,
			NetTaskConstructor constructor) {
		this.sa = sa;
		this.backlog = backlog;
		this.rsize = rsize;
		this.wsize = wsize;
		this.sslContext = sslContext;
		this.sslMode = sslMode;
		this.constructor = constructor;
		MBeans.register(NetModel.mbeans(), this,
				"limax.net.io:type=NetModel,name=ServerConfig-" + sa.toString().replace(':', '_'));
	}

	@Override
	public int getRecvBufferSize() {
		return rsize;
	}

	@Override
	public void setRecvBufferSize(int size) {
		rsize = size;
	}

	@Override
	public int getSendBufferSize() {
		return wsize;
	}

	@Override
	public void setSendBufferSize(int size) {
		wsize = size;
	}

	@Override
	public int getBacklog() {
		return backlog;
	}

	@Override
	public void setBacklog(int size) {
		this.backlog = size;
	}

	@Override
	public synchronized boolean isOpen() {
		return channel != null;
	}

	@Override
	public String getServiceName() {
		return constructor.getServiceName();
	}

	@Override
	public int getTaskCount() {
		return taskCounter.get();
	}

	public SSLExchange createSSLExchange() {
		return new SSLExchange(sslContext, sslMode);
	}
}
