package limax.util;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class OneTimePassword<K, V> {
	public static class FrequencyException extends RuntimeException {
		private static final long serialVersionUID = 5028049435575329660L;

		private FrequencyException() {
		}
	}

	private static final Random random = new SecureRandom();

	private final Map<K, Pair<Future<?>, V>> map = new ConcurrentHashMap<>();
	private final Function<Random, V> randomMapping;
	private final FrequencyConstraint<K> frequencyConstraint;
	private final ScheduledExecutorService scheduler;
	private final long timeout;

	public OneTimePassword(Function<Random, V> randomMapping, FrequencyConstraint<K> frequencyConstraint,
			ScheduledExecutorService scheduler, long timeout) {
		this.randomMapping = randomMapping;
		this.frequencyConstraint = frequencyConstraint;
		this.scheduler = scheduler;
		this.timeout = timeout;
	}

	public OneTimePassword(Function<Random, V> randomMapping, ScheduledExecutorService scheduler, long timeout) {
		this(randomMapping, null, scheduler, timeout);
	}

	public V generate(K k) throws FrequencyException {
		if (frequencyConstraint != null && !frequencyConstraint.check(k))
			throw new FrequencyException();
		V v = randomMapping.apply(random);
		Pair<Future<?>, V> pair = map.put(k,
				new Pair<>(scheduler.schedule(() -> map.remove(k, v), timeout, TimeUnit.MILLISECONDS), v));
		if (pair != null)
			pair.getKey().cancel(false);
		return v;
	}

	public boolean verify(K k, V v) {
		Pair<Future<?>, V> pair = map.remove(k);
		if (pair == null)
			return false;
		pair.getKey().cancel(false);
		return pair.getValue().equals(v);
	}
}
