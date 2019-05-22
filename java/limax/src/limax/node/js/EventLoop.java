package limax.node.js;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.script.Invocable;
import javax.script.ScriptEngine;

import limax.util.ConcurrentEnvironment;
import limax.util.HashExecutor;
import limax.util.Pair;
import limax.util.Trace;

public final class EventLoop {
	private final static ExecutorService executor = ConcurrentEnvironment.getInstance().newThreadPool(
			EventLoop.class.getName(), Integer.getInteger("limax.node.js.EventLoop.corePoolSize", 64), true);
	private final ScriptEngine engine;
	private final Invocable invocable;
	private final AtomicInteger refcount = new AtomicInteger();

	private final LinkedBlockingQueue<Pair<EventObject, Object[]>> queue = new LinkedBlockingQueue<>();

	private final static int ST_DONE = 1;
	private final static int ST_REF = 2;
	private final static int ST_ONCE = 4;
	private final static Object[] EMPTY = new Object[0];

	@FunctionalInterface
	public interface EventAction {
		void action(List<Object> result) throws Exception;
	}

	public class EventObject {
		private final Object callback;
		private int state = 0;

		EventObject(Object callback, boolean once) {
			ref();
			this.callback = callback;
			if (once)
				state |= ST_ONCE;
		}

		synchronized void run(Object[] args) throws Exception {
			if ((state & ST_DONE) != 0)
				return;
			try {
				if (callback != null)
					invocable.invokeMethod(callback, "call", null, args);
			} finally {
				if ((state & ST_ONCE) != 0)
					done();
			}
		}

		public synchronized void ref() {
			if ((state & ST_DONE) != 0)
				return;
			if ((state & ST_REF) != 0)
				return;
			state |= ST_REF;
			refcount.incrementAndGet();
		}

		public synchronized void unref() {
			if ((state & ST_DONE) != 0)
				return;
			if ((state & ST_REF) == 0)
				return;
			state &= ~ST_REF;
			refcount.decrementAndGet();
		}

		public synchronized void done() {
			unref();
			state |= ST_DONE;
		}

		public void queue() {
			queue(EMPTY);
		}

		public void queue(Object[] args) {
			queue.add(new Pair<EventObject, Object[]>(this, args));
		}
	}

	public class Callback {
		private final Object callback;

		Callback(Object callback) {
			this.callback = callback;
		}

		public void call(Object... args) {
			createEventObject(callback, true).queue(args);
		}
	}

	void launch() {
		while (refcount.intValue() > 0) {
			try {
				Pair<EventObject, Object[]> pair = queue.take();
				pair.getKey().run(pair.getValue());
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("EventLoop.launch", e);
			} finally {
			}
		}
	}

	EventLoop(ScriptEngine engine) {
		this.engine = engine;
		this.invocable = (Invocable) engine;
	}

	public EventObject createEventObject(Object obj, boolean once) {
		return new EventObject(obj, once);
	}

	public EventObject createEventObject() {
		return new EventObject(null, true);
	}

	public ScriptEngine getEngine() {
		return engine;
	}

	public Invocable getInvocable() {
		return invocable;
	}

	public void execute(Object callback, EventAction eaction) {
		EventObject evo = createEventObject(callback, true);
		executor.execute(() -> {
			List<Object> list = new ArrayList<>();
			try {
				list.add(null);
				eaction.action(list);
			} catch (Exception e) {
				list = Arrays.asList(e);
			} finally {
				evo.queue(list.toArray());
			}
		});
	}

	public void execute(Runnable r) {
		EventObject evo = createEventObject();
		executor.execute(() -> {
			try {
				r.run();
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("EventLoop.execute", e);
			} finally {
				evo.queue();
			}
		});
	}

	public Future<?> submit(Runnable r) {
		return executor.submit(r);
	}

	public Callback createCallback(Object callback) {
		return new Callback(callback);
	}

	public static HashExecutor createHashExecutor(int concurrencyLevel) {
		return ConcurrentEnvironment.getInstance().newHashExecutor(EventLoop.class.getName(), concurrencyLevel);
	}
}
