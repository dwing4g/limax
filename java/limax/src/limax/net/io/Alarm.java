package limax.net.io;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class Alarm {
	private final Runnable task;
	private Future<?> future;
	private long delay;
	private boolean update;

	public Alarm(Runnable task) {
		this.task = task;
	}

	public synchronized void reset(final long milliseconds) {
		if (update = delay == milliseconds)
			return;
		delay = milliseconds;
		if (future != null)
			future.cancel(false);
		future = milliseconds > 0 ? NetModel.delayPool.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				boolean done = false;
				synchronized (Alarm.this) {
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
			}
		}, milliseconds, milliseconds, TimeUnit.MILLISECONDS) : null;
	}
}