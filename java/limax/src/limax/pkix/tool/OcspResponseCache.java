package limax.pkix.tool;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import limax.codec.Octets;
import limax.util.Pair;

class OcspResponseCache {
	private static final int BUCKET_SHIFT_BITS = 5;
	private static final int BUCKET_SIZE = (1 << BUCKET_SHIFT_BITS);
	private static final int BUCKET_MASK = BUCKET_SIZE - 1;
	private final AtomicInteger size = new AtomicInteger();
	private final Map<Octets, Pair<Instant, byte[]>> cache = new ConcurrentHashMap<>();
	private final List<Map<BigInteger, List<Octets>>> indexes = new ArrayList<>();

	OcspResponseCache(int capacity) {
		for (int i = 0; i < BUCKET_SIZE; i++)
			indexes.add(Collections.synchronizedMap(new LinkedHashMap<BigInteger, List<Octets>>() {
				private static final long serialVersionUID = -3781288152469228029L;

				@Override
				protected boolean removeEldestEntry(Map.Entry<BigInteger, List<Octets>> eldest) {
					if (size.incrementAndGet() <= capacity)
						return false;
					size.decrementAndGet();
					cache.keySet().removeAll(eldest.getValue());
					return true;
				}
			}));
	}

	private Map<BigInteger, List<Octets>> index(BigInteger serialNumber) {
		return indexes.get(serialNumber.hashCode() & BUCKET_MASK);
	}

	byte[] get(Octets key) {
		Pair<Instant, byte[]> info = cache.get(key);
		return info == null || Instant.now().isAfter(info.getKey()) ? null : info.getValue();
	}

	void put(Octets key, OcspResponseInfo responseInfo) {
		for (BigInteger serialNumber : responseInfo.getSerialNumbers())
			index(serialNumber).computeIfAbsent(serialNumber, k -> new CopyOnWriteArrayList<>()).add(key);
		cache.put(key, new Pair<>(responseInfo.getNextUpdate(), responseInfo.getResponse()));
	}

	void invalidate(BigInteger serialNumber) {
		size.decrementAndGet();
		List<Octets> keys = index(serialNumber).remove(serialNumber);
		if (keys != null)
			cache.keySet().removeAll(keys);
	}
}
