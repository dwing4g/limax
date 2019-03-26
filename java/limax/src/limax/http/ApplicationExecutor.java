package limax.http;

import java.util.concurrent.atomic.AtomicBoolean;

import limax.net.Engine;

class ApplicationExecutor {
	private final Object key;
	private final AtomicBoolean active = new AtomicBoolean();

	ApplicationExecutor(Object key) {
		this.key = key;
	}

	public void execute(Runnable r) {
		Engine.getApplicationExecutor().execute(key, r);
	}

	public void executeExclusively(Runnable r) {
		if (active.compareAndSet(false, true))
			execute(() -> {
				active.set(false);
				r.run();
			});
	}
}
