package limax.node.js.modules;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.script.ScriptException;

import limax.node.js.EventLoop;
import limax.node.js.EventLoop.EventObject;
import limax.node.js.Module;
import limax.util.ConcurrentEnvironment;

public final class Timer implements Module {
	private final String threadName;
	private final ScheduledThreadPoolExecutor scheduler;
	private final EventLoop eventLoop;

	private static class Clearable {
		protected final EventObject evo;

		Clearable(EventObject evo) {
			this.evo = evo;
		}

		void clear() {
			evo.done();
		}
	}

	public static class Timeout extends Clearable {
		private final Future<?> future;

		Timeout(EventObject evo, Function<EventObject, Future<?>> setup) {
			super(evo);
			this.future = setup.apply(evo);
		}

		public void ref() {
			evo.ref();
		}

		public void unref() {
			evo.unref();
		}

		public void clear() {
			future.cancel(false);
			super.clear();
		}
	}

	public Timer(EventLoop eventLoop) throws Exception {
		this.threadName = Timer.class + "-" + eventLoop;
		this.scheduler = ConcurrentEnvironment.getInstance().newScheduledThreadPool(threadName, 1, true);
		this.eventLoop = eventLoop;
	}

	public Timeout setTimeout(Object callback, Number delay) throws ScriptException, NoSuchMethodException {
		return new Timeout(eventLoop.createEventObject(callback, true),
				evo -> scheduler.schedule(() -> evo.queue(), delay.longValue(), TimeUnit.MILLISECONDS));
	}

	public Timeout setInterval(Object callback, Number delay) throws ScriptException, NoSuchMethodException {
		return new Timeout(eventLoop.createEventObject(callback, false), evo -> scheduler
				.scheduleAtFixedRate(() -> evo.queue(), delay.longValue(), delay.longValue(), TimeUnit.MILLISECONDS));
	}

	public Clearable setImmediate(Object callback) {
		EventObject evo = eventLoop.createEventObject(callback, true);
		evo.queue();
		return new Clearable(evo);
	}

	public void clear(Object obj) {
		if (obj instanceof Clearable)
			((Clearable) obj).clear();
	}
}
