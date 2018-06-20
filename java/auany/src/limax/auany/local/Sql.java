package limax.auany.local;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import limax.util.Dispatcher;
import limax.util.Trace;

public class Sql implements Authenticate {

	private final ScheduledExecutorService scheduler;
	private final Dispatcher dispatcher;
	private final String url;
	private final Method op;
	private final BlockingQueue<RequestContext> contexts = new LinkedBlockingQueue<>();
	private final int timeout;
	private boolean stopped = false;

	private class RequestContext {
		private volatile Connection conn;
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

		void access(String username, String password, Consumer<Result> response) {
			ref.set(response);
			future = scheduler.schedule(() -> response(Result.Timeout), timeout, TimeUnit.MILLISECONDS);
			try {
				if (conn == null)
					conn = DriverManager.getConnection(url);
				response((boolean) op.invoke(null, conn, username, password) ? Result.Accept : Result.Reject);
			} catch (Throwable t) {
				if (Trace.isErrorEnabled())
					Trace.error("sql", t);
				close();
			}
		}

		void close() {
			response(Result.Fail);
			try {
				conn.close();
				conn = null;
			} catch (Exception e) {
			}
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
				RequestContext ctx = contexts.take();
				ctx.access(username, password, response);
				contexts.offer(ctx);
			} catch (Exception e) {
				response.accept(Result.Fail);
			}
		} , null);
	}

	@Override
	public synchronized void stop() {
		if (stopped)
			return;
		stopped = true;
		dispatcher.await();
		contexts.forEach(RequestContext::close);
	}

	public Sql(ScheduledExecutorService scheduler, String url, int pool, String classname, int timeout)
			throws Exception {
		this.scheduler = scheduler;
		this.dispatcher = new Dispatcher(scheduler);
		this.url = url;
		for (int i = 0; i < pool; i++)
			contexts.offer(new RequestContext());
		this.op = Class.forName(classname).getMethod("access", Connection.class, String.class, String.class);
		this.timeout = timeout;
	}
}
