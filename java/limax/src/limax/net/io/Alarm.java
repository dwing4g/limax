package limax.net.io;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class Alarm {
	private final Runnable task;
	private Future<?> future;
	private long delay;
	private boolean update;

	public Alarm(final Runnable task) {
		this.task = task;
	}

	public synchronized void reset(final long millisecond) {
		if (update = delay == millisecond)
			return;
		delay = millisecond;
		if (future != null)
			future.cancel(false);
		future = millisecond > 0 ? NetModel.delayPool.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				boolean done = false;
				synchronized (Alarm.this) {
					if (millisecond == delay)
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
		}, millisecond, millisecond, TimeUnit.MILLISECONDS) : null;
	}
}