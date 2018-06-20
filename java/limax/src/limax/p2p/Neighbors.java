package limax.p2p;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import limax.codec.Octets;
import limax.codec.OctetsStream;

public class Neighbors {
	private static final int BUCKET_SIZE = Integer.getInteger("limax.p2p.Neighbors.BUCKET_SIZE", 8);
	private static final long ENTRY_AGE_MAX = Long.getLong("limax.p2p.Neighbors.ENTRY_AGE_MAX", 1200000);

	private class Bucket {
		private final TreeMap<NetworkID, Future<?>> entries = new TreeMap<>(comparator);

		NetworkID add(NetworkID nid) {
			Future<?> future = entries.put(nid, scheduler.scheduleAtFixedRate(() -> {
				if (!ping.apply(nid.getInetSocketAddress()))
					Neighbors.this.remove(nid.getInetSocketAddress());
			}, ENTRY_AGE_MAX, ENTRY_AGE_MAX, TimeUnit.MILLISECONDS));
			if (future != null)
				future.cancel(false);
			if (entries.size() <= BUCKET_SIZE)
				return null;
			Map.Entry<NetworkID, Future<?>> entry = entries.pollLastEntry();
			entry.getValue().cancel(false);
			return entry.getKey();
		}

		void remove(NetworkID nid) {
			Future<?> future = entries.remove(nid);
			if (future != null)
				future.cancel(false);
		}

		Collection<NetworkID> getEntries() {
			return entries.keySet();
		}
	}

	private final Supplier<Collection<InetSocketAddress>> entranceSupplier;
	private final ScheduledExecutorService scheduler;
	private final TreeMap<Integer, Bucket> buckets = new TreeMap<>();
	private final Map<InetSocketAddress, Set<NetworkID>> addresses = new HashMap<>();
	private final Function<InetSocketAddress, Boolean> ping;
	private final DHTAddress local;
	private final Comparator<NetworkID> comparator;

	private int bucketId(DHTAddress addr) {
		return addr.distance(local).bitLength() - 1;
	}

	private int bucketId(NetworkID nid) {
		return bucketId(nid.getDHTAddress());
	}

	public Neighbors(ScheduledExecutorService scheduler, Supplier<Collection<InetSocketAddress>> entranceSupplier,
			Function<InetSocketAddress, Boolean> ping, byte[] encodedConfigData) {
		this.scheduler = scheduler;
		this.entranceSupplier = entranceSupplier;
		this.ping = ping;
		DHTAddress local;
		OctetsStream os = null;
		try {
			local = new DHTAddress(os = OctetsStream.wrap(Octets.wrap(encodedConfigData)));
		} catch (Exception e) {
			local = new DHTAddress();
		}
		this.local = local;
		Comparator<NetworkID> comparator = Comparator.comparing(nid -> nid.getDHTAddress().distance(this.local));
		this.comparator = comparator.thenComparing(nid -> nid.getSecondaryKey());
		if (os != null)
			try {
				int size = os.unmarshal_size();
				CountDownLatch latch = new CountDownLatch(size);
				for (int i = 0; i < size; i++) {
					NetworkID nid = new NetworkID(os);
					scheduler.execute(() -> {
						try {
							if (ping.apply(nid.getInetSocketAddress()))
								add(nid);
						} finally {
							latch.countDown();
						}
					});
				}
				latch.await();
			} catch (Exception e) {
			}
	}

	public byte[] encode() {
		Collection<NetworkID> entries = getNetworkIDs();
		int size = entries.size();
		OctetsStream os = new OctetsStream().marshal(local).marshal_size(size);
		entries.forEach(nid -> os.marshal(nid));
		return os.getBytes();
	}

	public synchronized int size() {
		int size = 0;
		for (Collection<NetworkID> nids : addresses.values())
			size += nids.size();
		return size;
	}

	public DHTAddress getLocalDHTAddress() {
		return local;
	}

	public synchronized Collection<InetSocketAddress> getInetSocketAddresses() {
		return new ArrayList<>(addresses.keySet());
	}

	public synchronized Collection<NetworkID> getNetworkIDs() {
		return addresses.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
	}

	public synchronized void add(NetworkID nid) {
		int bucketId = bucketId(nid);
		if (bucketId >= 0) {
			addresses.computeIfAbsent(nid.getInetSocketAddress(), k -> new HashSet<>()).add(nid);
			NetworkID old = buckets.computeIfAbsent(bucketId, k -> new Bucket()).add(nid);
			if (old != null)
				addresses.get(old.getInetSocketAddress()).remove(old);
		}
	}

	public synchronized void addAll(Collection<NetworkID> nids) {
		nids.forEach(nid -> add(nid));
	}

	public synchronized void remove(InetSocketAddress inetSocketAddress) {
		Collection<NetworkID> nids = addresses.remove(inetSocketAddress);
		if (nids != null)
			nids.forEach(nid -> buckets.get(bucketId(nid)).remove(nid));
	}

	public synchronized void removeAll(Collection<InetSocketAddress> inetSocketAddresses) {
		inetSocketAddresses.forEach(inetAddress -> remove(inetAddress));
	}

	public Collection<InetSocketAddress> search() {
		return entranceSupplier.get();
	}

	public synchronized Collection<NetworkID> search(DHTAddress searchFor, int limit) {
		List<NetworkID> candidates = new ArrayList<>();
		int bucketId = bucketId(searchFor) + 1;
		buckets.headMap(bucketId, true).descendingMap().values().stream().map(Bucket::getEntries)
				.flatMap(Collection::stream).collect(() -> candidates, List::add, List::addAll);
		if (candidates.size() > limit) {
			return candidates.stream().sorted(comparator).limit(limit).collect(Collectors.toList());
		} else if (candidates.size() < limit)
			buckets.tailMap(bucketId, false).values().stream().map(Bucket::getEntries).flatMap(Collection::stream)
					.limit(limit - candidates.size()).collect(() -> candidates, List::add, List::addAll);
		return candidates;
	}

	public Collection<NetworkID> searchNode(DHTAddress searchFor, NetworkSearch searcher, Collection<NetworkID> base,
			Supplier<Collection<InetSocketAddress>> entranceAddressesSupplier, int anticipantion,
			int concurrencyLevel) {
		return new DHTSearch.SearchNode(searchFor, searcher, anticipantion, concurrencyLevel, nid -> add(nid),
				inaddr -> remove(inaddr), entranceAddressesSupplier).get(base);
	}

	public Collection<InetSocketAddress> searchResource(DHTAddress searchFor, NetworkSearch searcher,
			Supplier<Collection<InetSocketAddress>> entranceAddressesSupplier, Collection<NetworkID> base,
			int anticipantion, int concurrencyLevel) {
		return new DHTSearch.SearchResource(searchFor, searcher, anticipantion, concurrencyLevel, nid -> add(nid),
				inaddr -> remove(inaddr), entranceAddressesSupplier).get(base);
	}
}
