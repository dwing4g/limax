package limax.util;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReferenceGuard<T> {
	private final Map<T, AtomicInteger> map = Collections.synchronizedMap(new WeakHashMap<>());

	public void await(T obj) throws InterruptedException {
		AtomicInteger v = map.get(obj);
		if (v != null)
			synchronized (v) {
				while (v.get() != 0)
					v.wait();
			}
	}

	public Runnable enter(T obj) {
		AtomicInteger n = new AtomicInteger();
		AtomicInteger o = map.putIfAbsent(obj, n);
		AtomicInteger v = o == null ? n : o;
		v.incrementAndGet();
		return () -> {
			if (v.decrementAndGet() == 0)
				synchronized (v) {
					v.notify();
				}
		};
	}
}
