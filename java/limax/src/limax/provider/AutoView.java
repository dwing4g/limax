package limax.provider;

import java.util.concurrent.TimeUnit;

import limax.net.Engine;

abstract class AutoView extends View {
	private boolean ticking;
	private boolean trigger;
	private long tick;

	protected AutoView(CreateParameter param, String[] prefix, byte[][] collectors, int cycle) {
		super(param, prefix, collectors, cycle);
		setTick(param.getViewStub().getTick());
	}

	public void setTick(long milliseconds) {
		this.tick = milliseconds < 0 ? 0 : milliseconds;
	}

	public long getTick() {
		return this.tick;
	}

	void schedule() {
		trigger = true;
		if (!ticking && !isClosed()) {
			ticking = true;
			Engine.getProtocolScheduler().schedule(() -> schedule(() -> {
				trigger = false;
				flush();
				ticking = false;
				if (trigger)
					schedule();
			}), tick, TimeUnit.MILLISECONDS);
		}
	}

	abstract void flush();
}
