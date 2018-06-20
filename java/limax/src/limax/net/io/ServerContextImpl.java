package limax.net.io;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

import limax.util.MBeans;

class ServerContextImpl implements ServerContextMXBean {
	private final SocketAddress sa;
	private final NetTaskConstructor constructor;
	final AtomicInteger taskCounter = new AtomicInteger();
	private volatile int backlog;
	private volatile int rsize;
	private volatile int wsize;
	private volatile boolean closed = true;
	private ServerSocketChannel channel;

	public synchronized void open() throws IOException {
		if (closed) {
			channel = ServerSocketChannel.open();
			channel.configureBlocking(false);
			channel.socket().setReuseAddress(true);
			channel.socket().bind(sa, backlog);
			NetModel.pollPolicy.addChannel(channel, SelectionKey.OP_ACCEPT, this);
			closed = false;
		}
	}

	public synchronized void close() throws IOException {
		if (!closed) {
			channel.close();
			closed = true;
		}
	}

	ServerContextImpl(SocketAddress sa, int backlog, int rsize, int wsize, NetTaskConstructor constructor) {
		this.sa = sa;
		this.backlog = backlog;
		this.rsize = rsize;
		this.wsize = wsize;
		this.constructor = constructor;
		MBeans.register(NetModel.mbeans(), this,
				"limax.net.io:type=NetModel,name=ServerConfig-" + sa.toString().replace(':', '_'));
	}

	NetOperation newInstance() {
		return (NetOperation) constructor.newInstance(this);
	}

	@Override
	public int getReadBufferSize() {
		return rsize;
	}

	@Override
	public void setReadBufferSize(int size) {
		rsize = size;
	}

	@Override
	public int getWriteBufferSize() {
		return wsize;
	}

	@Override
	public void setWriteBufferSize(int size) {
		wsize = size;
	}

	@Override
	public int getBacklog() {
		return backlog;
	}

	@Override
	public void setBacklog(int size) {
		backlog = size;
	}

	@Override
	public boolean isOpen() {
		return !closed;
	}

	@Override
	public String getServiceName() {
		return constructor.getServiceName();
	}

	@Override
	public int getTaskCount() {
		return taskCounter.get();
	}
}