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
import limax.util.JMXException;
import limax.util.MBeans;
import limax.util.Resource;

public final class NetModel {
	private final static int delayPoolSize = Integer.getInteger("limax.net.io.NetModel.delayPoolSize", 1).intValue();
	static PollPolicy pollPolicy;
	static HashExecutor processPool;
	static ScheduledThreadPoolExecutor delayPool;
	private static Resource mbeans;

	static Resource mbeans() {
		return mbeans;
	}

	private NetModel() {
	}

	public static void initialize(PollPolicy pp, int processPoolSize) throws IOException, JMXException {
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
				List<Integer> l = new ArrayList<Integer>();
				for (PollTask pollTask : pollPolicy.q)
					l.add(pollTask.size());
				return l;
			}
		}, "limax.net.io:type=NetModel,name=TaskState");
	}

	public static void uninitialize() {
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

	public static WebSocketTask createWebSocketServerTask(ServerContext context, WebSocketProcessor processor) {
		throw new UnsupportedOperationException();
	}

	public static WebSocketTask createWebSocketServerTask(ServerContext context, WebSocketProcessor processor,
			SSLContext sslContext) {
		throw new UnsupportedOperationException();
	}
}
