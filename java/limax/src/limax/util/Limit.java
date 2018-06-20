package limax.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class Limit {
	private final static Map<String, Limit> limits = new ConcurrentHashMap<>();
	private final AtomicLong limit;

	private Limit(long limit) {
		if (limit <= 0)
			throw new java.lang.IllegalArgumentException("limit <= 0");
		this.limit = new AtomicLong(limit);
	}

	public boolean request() {
		return limit.getAndUpdate(x -> x == 0 ? 0 : x - 1) > 0;
	}

	public void reclaim() {
		limit.incrementAndGet();
	}

	public static void put(String name, long limit) {
		limits.putIfAbsent(name, new Limit(limit));
	}

	public static Limit get(String name) {
		return limits.computeIfAbsent(name, k -> new Limit(Long.MAX_VALUE));
	}
}
