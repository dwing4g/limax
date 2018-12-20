package limax.auany.local;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import limax.sql.SQLPooledExecutor;
import limax.util.Dispatcher;
import limax.util.Trace;

public class Sql implements Authenticate {
	private final ScheduledExecutorService scheduler;
	private final SQLPooledExecutor sqlExecutor;
	private final Dispatcher dispatcher;
	private final Method op;
	private final BlockingQueue<RequestContext> contexts = new LinkedBlockingQueue<>();
	private final int timeout;
	private boolean stopped = false;

	private class RequestContext {
		private volatile Future<?> future;
		private volatile AtomicReference<Consumer<Result>> ref = new AtomicReference<>();

		RequestContext() {
		}

		void response(Result r) {
			Consumer<Result> response = ref.getAndSet(null);
			if (response != null) {
				response.accept(r);
				future.cancel(false);
			}
		}

		void access(String username, String password, Consumer<Result> response, long timeout) {
			ref.set(response);
			future = scheduler.schedule(() -> response(Result.Timeout), timeout, TimeUnit.MILLISECONDS);
			try {
				sqlExecutor.execute(conn -> {
					try {
						response(op.invoke(null, conn, username, password).equals(Boolean.TRUE) ? Result.Accept
								: Result.Reject);
					} catch (InvocationTargetException e) {
						throw (Exception) e.getCause();
					}
				});
			} catch (Throwable t) {
				if (Trace.isDebugEnabled())
					Trace.debug("sql", t);
				fail();
			}
		}

		void fail() {
			response(Result.Fail);
		}
	}

	@Override
	public synchronized void access(String username, String password, Consumer<Result> response) {
		if (stopped) {
			response.accept(Result.Fail);
			return;
		}
		dispatcher.execute(() -> {
			try {
				long start = System.currentTimeMillis();
				RequestContext ctx = contexts.poll(timeout, TimeUnit.MILLISECONDS);
				if (ctx != null) {
					ctx.access(username, password, response, timeout - (System.currentTimeMillis() - start));
					contexts.offer(ctx);
				} else {
					response.accept(Result.Timeout);
				}
			} catch (Exception e) {
				response.accept(Result.Fail);
			}
		}, null);
	}

	@Override
	public synchronized void stop() {
		if (stopped)
			return;
		stopped = true;
		dispatcher.await();
		contexts.forEach(RequestContext::fail);
		sqlExecutor.shutdown();
	}

	public Sql(ScheduledExecutorService scheduler, String url, int pool, String classname, int timeout)
			throws Exception {
		this.scheduler = scheduler;
		this.sqlExecutor = new SQLPooledExecutor(url, 1);
		this.dispatcher = new Dispatcher(scheduler);
		for (int i = 0; i < pool; i++)
			contexts.offer(new RequestContext());
		this.op = Class.forName(classname).getMethod("access", Connection.class, String.class, String.class);
		this.timeout = timeout;
	}
}
