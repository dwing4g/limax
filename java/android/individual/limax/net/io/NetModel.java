package limax.net.io;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import javax.net.ssl.SSLContext;

import limax.util.ConcurrentEnvironment;
import limax.util.HashExecutor;
import limax.util.MBeans;
import limax.util.Resource;

public final class NetModel {
	public final static int SSL_NEED_CLIENT_AUTH = 1;
	public final static int SSL_WANT_CLIENT_AUTH = 2;
	final static int SSL_SERVER_MODE = 4;

	private final static int delayPoolSize = Integer.getInteger("limax.net.io.NetModel.delayPoolSize", 1).intValue();
	private final static List<ServerContext> serverContexts = new ArrayList<ServerContext>();
	private static volatile boolean closed = true;
	static PollPolicy pollPolicy;
	static HashExecutor processPool;
	static ScheduledThreadPoolExecutor delayPool;
	private static Resource mbeans;

	static Resource mbeans() {
		return mbeans;
	}

	private NetModel() {
	}

	public static synchronized void initialize(PollPolicy pp, int processPoolSize) throws IOException {
		if (!closed)
			return;
		ConcurrentEnvironment env = ConcurrentEnvironment.getInstance();
		pollPolicy = pp;
		env.newFixedThreadPool("limax.net.io.NetModel.processPool", processPoolSize);
		processPool = env.newHashExecutor("limax.net.io.NetModel.processPool", processPoolSize * 16);
		delayPool = env.newScheduledThreadPool("limax.net.io.NetModel.delayPool", delayPoolSize);
		mbeans = MBeans.register(MBeans.root(), new TaskStateMXBean() {
			public long getTotal() {
				return AbstractNetTask.total.longValue();
			}

			public long getRunning() {
				return AbstractNetTask.running.longValue();
			}

			public List<Integer> getPollTaskSize() {
				List<Integer> l = new ArrayList<Integer>();
				for (PollTask pollTask : pollPolicy.q)
					l.add(pollTask.size());
				return l;
			}
		}, "limax.net.io:type=NetModel,name=TaskState");
		closed = false;
	}

	public static synchronized void unInitialize() {
		if (closed)
			return;
		ConcurrentEnvironment env = ConcurrentEnvironment.getInstance();
		for (ServerContext context : serverContexts)
			try {
				context.close();
			} catch (IOException e) {
			}
		serverContexts.clear();
		pollPolicy.shutdown();
		env.shutdown("limax.net.io.NetModel.delayPool", "limax.net.io.NetModel.processPool");
		mbeans.close();
		closed = true;
	}

	public static ServerContext addServer(SocketAddress sa, int backlog, int rsize, int wsize, SSLContext sslContext,
			int sslMode, ServerContext.NetTaskConstructor constructor, boolean autoOpen, boolean asynchronous)
			throws IOException {
		if (closed)
			throw new IllegalStateException("NetModel closed");
		PollServerContext context = new PollServerContext(sa, backlog, rsize, wsize, sslContext, sslMode, constructor);
		if (autoOpen)
			context.open();
		synchronized (NetModel.class) {
			serverContexts.add(context);
		}
		return context;
	}

	public static void addClient(SocketAddress sa, NetTask task) throws IOException {
		if (closed)
			throw new IllegalStateException("NetModel closed");
		SocketChannel sc = SocketChannel.open();
		sc.configureBlocking(false);
		sc.connect(sa);
		pollPolicy.addChannel(sc, SelectionKey.OP_CONNECT, task);
	}

	public static NetTask createClientTask(int rsize, int wsize, SSLContext sslContext, NetProcessor processor,
			boolean asynchronous) {
		if (closed)
			throw new IllegalStateException("NetModel closed");
		return new PollClientTask(rsize, wsize, sslContext, processor);
	}

	public static NetTask createServerTask(ServerContext context, NetProcessor processor) {
		if (closed)
			throw new IllegalStateException("NetModel closed");
		return new PollServerTask((AbstractServerContext) context, processor);
	}
}
