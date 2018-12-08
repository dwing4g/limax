package limax.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FrequencyConstraint<K> {
	private final Map<K, AtomicBoolean> map = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduler;
	private final long frequency;

	public FrequencyConstraint(ScheduledExecutorService scheduler, long frequency) {
		this.scheduler = scheduler;
		this.frequency = frequency;
	}

	public boolean check(K k) {
		return map.computeIfAbsent(k, _k -> {
			scheduler.schedule(() -> map.remove(k), frequency, TimeUnit.MILLISECONDS);
			return new AtomicBoolean(false);
		}).compareAndSet(false, true);
	}
}
