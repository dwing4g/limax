package limax.net.io;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;

import limax.util.ConcurrentEnvironment;
import limax.util.HashExecutor;
import limax.util.MBeans;
import limax.util.Resource;

public final class NetModel {
	public final static int SSL_NEED_CLIENT_AUTH = 1;
	public final static int SSL_WANT_CLIENT_AUTH = 2;
	final static int SSL_SERVER_MODE = 4;

	private final static int delayPoolSize = Integer.getInteger("limax.net.io.NetModel.delayPoolSize", 1);
	private final static List<ServerContext> serverContexts = new ArrayList<>();
	private final static Set<AsynchronousNetTask> asynchronousNetTasks = new HashSet<>();
	private static volatile boolean closed = true;
	static PollPolicy pollPolicy;
	static HashExecutor processPool;
	static ScheduledThreadPoolExecutor delayPool;
	static AsynchronousChannelGroup group;
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
		group = AsynchronousChannelGroup
				.withThreadPool(env.newFixedThreadPool("limax.net.io.NetModel.processPool", processPoolSize));
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
				return pollPolicy.q.stream().map(PollTask::size).collect(Collectors.toList());
			}
		}, "limax.net.io:type=NetModel,name=TaskState");
		closed = false;
	}

	public static synchronized void unInitialize() {
		if (closed)
			return;
		closed = true;
		ConcurrentEnvironment env = ConcurrentEnvironment.getInstance();
		serverContexts.forEach(context -> {
			try {
				context.close();
			} catch (IOException e) {
			}
		});
		serverContexts.clear();
		synchronized (asynchronousNetTasks) {
			asynchronousNetTasks.forEach(AbstractNetTask::onServiceShutdown);
		}
		pollPolicy.shutdown();
		synchronized (asynchronousNetTasks) {
			while (!asynchronousNetTasks.isEmpty())
				try {
					asynchronousNetTasks.wait();
				} catch (InterruptedException e) {
				}
		}
		group.shutdown();
		env.shutdown("limax.net.io.NetModel.delayPool", "limax.net.io.NetModel.processPool");
		mbeans.close();
	}

	static void add(AsynchronousNetTask task) {
		synchronized (asynchronousNetTasks) {
			asynchronousNetTasks.add(task);
		}
	}

	static void remove(AsynchronousNetTask task) {
		synchronized (asynchronousNetTasks) {
			asynchronousNetTasks.remove(task);
			if (asynchronousNetTasks.isEmpty())
				asynchronousNetTasks.notify();
		}
	}

	public static ServerContext addServer(SocketAddress sa, int backlog, int rsize, int wsize, SSLContext sslContext,
			int sslMode, ServerContext.NetTaskConstructor constructor, boolean autoOpen, boolean asynchronous)
			throws IOException {
		if (closed)
			throw new IllegalStateException("NetModel closed");
		sslMode |= SSL_SERVER_MODE;
		ServerContext context = asynchronous
				? new AsynchronousServerContext(sa, backlog, rsize, wsize, sslContext, sslMode, constructor)
				: new PollServerContext(sa, backlog, rsize, wsize, sslContext, sslMode, constructor);
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
		if (task instanceof PollNetTask) {
			SocketChannel sc = SocketChannel.open();
			sc.configureBlocking(false);
			sc.connect(sa);
			pollPolicy.addChannel(sc, SelectionKey.OP_CONNECT, task);
		} else {
			((AsynchronousNetTask) task).connect(sa);
		}
	}

	public static NetTask createClientTask(int rsize, int wsize, SSLContext sslContext, NetProcessor processor,
			boolean asynchronous) {
		if (closed)
			throw new IllegalStateException("NetModel closed");
		return asynchronous ? new AsynchronousClientTask(rsize, wsize, sslContext, processor)
				: new PollClientTask(rsize, wsize, sslContext, processor);
	}

	public static NetTask createServerTask(ServerContext context, NetProcessor processor) {
		if (closed)
			throw new IllegalStateException("NetModel closed");
		return context instanceof PollServerContext ? new PollServerTask((AbstractServerContext) context, processor)
				: new AsynchronousServerTask((AbstractServerContext) context, processor);
	}

	private static class AlarmImpl implements Alarm {
		private final Runnable task;
		private Future<?> future;
		private long delay;
		private boolean update;

		public AlarmImpl(Runnable task) {
			this.task = task;
		}

		@Override
		public synchronized void reset(long milliseconds) {
			if (update = delay == milliseconds)
				return;
			delay = milliseconds;
			if (future != null)
				future.cancel(false);
			future = milliseconds > 0 ? NetModel.delayPool.scheduleWithFixedDelay(() -> {
				boolean done = false;
				synchronized (AlarmImpl.this) {
					if (milliseconds == delay)
						if (update) {
							update = false;
						} else {
							future.cancel(false);
							future = null;
							delay = 0;
							done = true;
						}
				}
				if (done)
					task.run();
			}, milliseconds, milliseconds, TimeUnit.MILLISECONDS) : null;
		}

	}

	static Alarm createAlarm(Runnable action) {
		return new AlarmImpl(action);
	}
}
