package limax.key;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import limax.util.Helper;
import limax.util.Pair;
import limax.util.Trace;

class MasterKeyContainer {
	private static final int MASTER_KEY_SIZE = Integer.getInteger("limax.key.MasterKeyContainer.MASTER_KEY_SIZE", 64);

	class Digester {
		private final long timestamp;
		private final byte[] masterKey;
		private final MessageDigest md;

		private Digester(long timestamp, byte[] masterKey) throws NoSuchAlgorithmException {
			this.timestamp = timestamp;
			this.masterKey = masterKey;
			this.md = MessageDigest.getInstance(algorithm);
			ByteBuffer bb = ByteBuffer.allocate(8).putLong(timestamp);
			bb.rewind();
			this.md.update(bb);
			this.md.update(masterKey);
		}

		KeyResponse sign(KeyIdent keyIdent, X509Certificate cert) {
			try {
				keyIdent.setTimestamp(timestamp);
				MessageDigest md = (MessageDigest) this.md.clone();
				md.update(keyIdent.toString().getBytes(StandardCharsets.UTF_8));
				md.update(cert.getIssuerX500Principal().getEncoded());
				return new KeyResponse(timestamp, md.digest(), randomServers);
			} catch (CloneNotSupportedException e) {
				return null;
			}
		}

		@Override
		public String toString() {
			try {
				return timestamp + " " + Helper.toHexString(((MessageDigest) md.clone()).digest());
			} catch (CloneNotSupportedException e) {
				return "";
			}
		}
	}

	private final ScheduledExecutorService scheduler;
	private final TreeMap<Long, Digester> map = new TreeMap<>();
	private final String algorithm;
	private final long lifespan;
	private volatile Digester digester;
	private volatile Collection<InetAddress> randomServers;

	MasterKeyContainer(ScheduledExecutorService scheduler, String algorithm, long lifespan) {
		this.scheduler = scheduler;
		this.algorithm = algorithm;
		this.lifespan = lifespan;
	}

	Digester getDigester() {
		return digester;
	}

	synchronized Digester getDigester(long timestamp) {
		return map.get(timestamp);
	}

	synchronized Collection<Long> getTimestamps() {
		return new ArrayList<>(map.keySet());
	}

	synchronized Collection<Pair<Long, byte[]>> diff(Set<Long> timestamps) {
		Set<Long> own = new HashSet<>(map.keySet());
		own.removeAll(timestamps);
		timestamps.removeAll(map.keySet());
		return collect(own);
	}

	synchronized Collection<Pair<Long, byte[]>> collect(Collection<Long> timestamps) {
		return timestamps.stream().filter(timestamp -> map.containsKey(timestamp))
				.map(timestamp -> new Pair<>(timestamp, map.get(timestamp).masterKey)).collect(Collectors.toList());
	}

	synchronized void merge(Collection<Pair<Long, byte[]>> collect) {
		if (collect.stream().filter(pair -> {
			long timestamp = pair.getKey();
			if (map.containsKey(timestamp))
				return false;
			try {
				map.put(timestamp, new Digester(timestamp, pair.getValue()));
				scheduler.schedule(() -> {
					synchronized (MasterKeyContainer.this) {
						map.remove(timestamp);
					}
				}, timestamp + lifespan - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
			} catch (NoSuchAlgorithmException e) {
			}
			return true;
		}).count() > 0) {
			long timestamp = digester != null ? digester.timestamp : 0;
			digester = (map.size() > 1 ? map.lowerEntry(map.lastKey()) : map.firstEntry()).getValue();
			if (timestamp != digester.timestamp)
				if (Trace.isInfoEnabled())
					Trace.info("MasterKeyContainer CurrentDigester " + digester);
		}
	}

	private long publish() {
		long timestamp = System.currentTimeMillis();
		merge(Arrays.asList(new Pair<>(timestamp, Helper.makeRandValues(MASTER_KEY_SIZE))));
		return timestamp;
	}

	private void schedulePublish(long timestamp, long period, Consumer<Long> consumer) {
		scheduler.schedule(() -> {
			synchronized (this) {
				long current = map.lastKey();
				if (timestamp == current)
					consumer.accept(publish());
				schedulePublish(current, period, consumer);
			}
		}, timestamp + period - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
	}

	synchronized void start(long rekeyPeriod, Consumer<Long> consumer) {
		if (rekeyPeriod == -1)
			return;
		if (map.isEmpty())
			consumer.accept(publish());
		schedulePublish(map.lastKey(), TimeUnit.DAYS.toMillis(rekeyPeriod), consumer);
	}

	void setRandomServers(Collection<InetAddress> randomServers) {
		this.randomServers = randomServers;
	}
}
