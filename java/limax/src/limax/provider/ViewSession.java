package limax.provider;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

final class ViewSession {
	private final Map<Short, SessionView> sessionviews = new HashMap<>();
	private final Map<Long, TemporaryView> temporaryviews = new HashMap<>();
	private boolean closed = false;

	private long combine(short a, int b) {
		return ((long) a << 32) + b;
	}

	synchronized SessionView findSessionView(short classindex) {
		return closed ? null : sessionviews.get(classindex);
	}

	synchronized TemporaryView findTemporaryView(short classindex, int instanceindex) {
		return closed ? null : temporaryviews.get(combine(classindex, instanceindex));
	}

	void add(SessionView view) {
		sessionviews.put(view.getClassIndex(), view);
	}

	synchronized void add(TemporaryView view) {
		if (!closed)
			temporaryviews.put(combine(view.getClassIndex(), view.getInstanceIndex()), view);
	}

	synchronized TemporaryView remove(TemporaryView view) {
		return closed ? null : temporaryviews.remove(combine(view.getClassIndex(), view.getInstanceIndex()));
	}

	void close(long sessionid, boolean sync) {
		View.schedule(sessionid, () -> {
			synchronized (this) {
				closed = true;
			}
		});
		if (sync) {
			CountDownLatch c = new CountDownLatch(temporaryviews.size());
			temporaryviews.values().forEach(v -> v.close(sessionid, () -> c.countDown()));
			while (true)
				try {
					c.await();
					break;
				} catch (InterruptedException e) {
				}
		} else
			temporaryviews.values().forEach(v -> v.close(sessionid));
		sessionviews.values().forEach(v -> v.close());
	}

	synchronized boolean isClosed() {
		return closed;
	}
}