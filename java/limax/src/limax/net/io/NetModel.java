package limax.net.io;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;

import limax.util.ConcurrentEnvironment;
import limax.util.HashExecutor;
import limax.util.MBeans;
import limax.util.Resource;

public final class NetModel {
	private final static int delayPoolSize = Integer.getInteger("limax.net.io.NetModel.delayPoolSize", 1);
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
		ConcurrentEnvironment env = ConcurrentEnvironment.getInstance();
		pollPolicy = pp;
		env.newFixedThreadPool("limax.net.io.NetModel.processPool", processPoolSize);
		processPool = env.newHashExecutor("limax.net.io.NetModel.processPool", processPoolSize * 16);
		delayPool = env.newScheduledThreadPool("limax.net.io.NetModel.delayPool", delayPoolSize);
		mbeans = MBeans.register(MBeans.root(), new TaskStateMXBean() {
			public long getTotal() {
				return NetTaskImpl.total.longValue();
			}

			public long getRunning() {
				return NetTaskImpl.running.longValue();
			}

			public List<Integer> getPollTaskSize() {
				return pollPolicy.q.stream().map(PollTask::size).collect(Collectors.toList());
			}
		}, "limax.net.io:type=NetModel,name=TaskState");
	}

	public static synchronized void unInitialize() {
		ConcurrentEnvironment env = ConcurrentEnvironment.getInstance();
		pollPolicy.cleanup();
		env.shutdown("limax.net.io.NetModel.delayPool", "limax.net.io.NetModel.processPool");
		mbeans.close();
	}

	public static ServerContext addServer(SocketAddress sa, int backlog, int rsize, int wsize,
			ServerContext.NetTaskConstructor constructor, boolean autoOpen) throws IOException {
		ServerContextImpl context = new ServerContextImpl(sa, backlog, rsize, wsize, constructor);
		if (autoOpen)
			context.open();
		return context;
	}

	public static void addClient(SocketAddress sa, NetTask task) throws IOException {
		SocketChannel sc = SocketChannel.open();
		sc.configureBlocking(false);
		sc.connect(sa);
		pollPolicy.addChannel(sc, SelectionKey.OP_CONNECT, task);
	}

	public static NetTask createClientTask(int rsize, int wsize, NetProcessor processor) {
		return new ClientTask(rsize, wsize, processor);
	}

	public static NetTask createServerTask(ServerContext context, NetProcessor processor) {
		return new ServerTask((ServerContextImpl) context, processor);
	}

	public static NetTask createServerTask(ServerContext context, NetProcessor processor, SSLContext sslContext) {
		if (sslContext == null)
			throw new NullPointerException();
		return new SSLServerTask((ServerContextImpl) context, processor, sslContext);
	}

	public static WebSocketTask createWebSocketServerTask(ServerContext context, WebSocketProcessor processor) {
		return new WebSocketServerTask((ServerContextImpl) context, processor);
	}

	public static WebSocketTask createWebSocketServerTask(ServerContext context, WebSocketProcessor processor,
			SSLContext sslContext) {
		if (sslContext == null)
			throw new NullPointerException();
		return new WebSocketServerTask((ServerContextImpl) context, processor, sslContext);
	}
}
