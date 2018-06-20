package limax.p2p;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import limax.util.Pair;

abstract class DHTSearch {
	private final DHTAddress searchFor;
	private final NetworkSearch searcher;
	private final ExecutorService executor;
	private final Set<InetSocketAddress> visited = new HashSet<>();
	private final Phaser phaser = new Phaser(1);
	private final Consumer<NetworkID> confirmNetworkID;
	private final Consumer<InetSocketAddress> rejectInetSocketAddress;
	private final Supplier<Collection<InetSocketAddress>> inetSocketAddressSupplier;
	final Comparator<NetworkID> comparator;
	private Function<NetworkID, SearchTask> nid2task;

	void execute(Runnable r) {
		phaser.register();
		executor.execute(r);
	}

	class SearchTask implements Runnable {
		private final Callable<Collection<NetworkID>> task;

		SearchTask(Callable<Collection<NetworkID>> task) {
			this.task = task;
		}

		@Override
		public void run() {
			try {
				Collection<NetworkID> nids = task.call();
				synchronized (visited) {
					nids.stream().filter(nid -> visited.add(nid.getInetSocketAddress()))
							.forEach(nid -> execute(nid2task.apply(nid)));
				}
			} catch (Exception e) {
			} finally {
				phaser.arriveAndDeregister();
			}
		}
	}

	DHTSearch(DHTAddress searchFor, NetworkSearch searcher, int concurrencyLevel, Consumer<NetworkID> confirmNetworkID,
			Consumer<InetSocketAddress> rejectInetSocketAddress,
			Supplier<Collection<InetSocketAddress>> inetSocketAddressSupplier) {
		this.searchFor = searchFor;
		this.searcher = searcher;
		this.executor = Executors.newFixedThreadPool(concurrencyLevel);
		this.confirmNetworkID = confirmNetworkID;
		this.rejectInetSocketAddress = rejectInetSocketAddress;
		this.inetSocketAddressSupplier = inetSocketAddressSupplier;
		Comparator<NetworkID> comparator = Comparator.comparing(nid -> nid.getDHTAddress().distance(this.searchFor));
		this.comparator = comparator.thenComparing(nid -> nid.getSecondaryKey());
	}

	Pair<Boolean, Collection<NetworkID>> search(InetSocketAddress inetSocketAddress) throws Exception {
		try {
			return searcher.apply(searchFor, inetSocketAddress);
		} catch (Exception e) {
			rejectInetSocketAddress.accept(inetSocketAddress);
			throw e;
		}
	}

	Pair<Boolean, Collection<NetworkID>> search(NetworkID nid) throws Exception {
		Pair<Boolean, Collection<NetworkID>> r = search(nid.getInetSocketAddress());
		confirmNetworkID.accept(nid);
		return r;
	}

	void search(Function<NetworkID, SearchTask> nid2task, Collection<NetworkID> base) {
		this.nid2task = nid2task;
		base.stream().filter(nid -> visited.add(nid.getInetSocketAddress()))
				.forEach(nid -> execute(nid2task.apply(nid)));
		phaser.arriveAndAwaitAdvance();
		if (!sufficient()) {
			inetSocketAddressSupplier.get().stream().filter(inetSocketAddress -> visited.add(inetSocketAddress))
					.forEach(inetSocketAddress -> execute(new SearchTask(() -> search(inetSocketAddress).getValue())));
			phaser.arriveAndAwaitAdvance();
		}
		executor.shutdown();
	}

	abstract boolean sufficient();

	static class SearchHolder {
		private final TreeSet<NetworkID> result;
		private final int anticipantion;

		boolean sufficient() {
			return result.size() >= anticipantion;
		}

		SearchHolder(Comparator<NetworkID> comparator, int anticipantion) {
			this.result = new TreeSet<>(comparator);
			this.anticipantion = anticipantion;
		}

		synchronized Collection<NetworkID> update(NetworkID initiator, Collection<NetworkID> response) {
			result.add(initiator);
			return result.size() > anticipantion && result.pollLast().equals(initiator) ? Collections.emptyList()
					: response;
		}

		Collection<NetworkID> getResult() {
			return result;
		}
	}

	static class SearchNode extends DHTSearch {
		private final SearchHolder holder;

		@Override
		boolean sufficient() {
			return holder.sufficient();
		}

		SearchNode(DHTAddress searchFor, NetworkSearch searcher, int anticipantion, int concurrencyLevel,
				Consumer<NetworkID> confirmNetworkID, Consumer<InetSocketAddress> rejectInetSocketAddress,
				Supplier<Collection<InetSocketAddress>> inetSocketAddressSupplier) {
			super(searchFor, searcher, concurrencyLevel, confirmNetworkID, rejectInetSocketAddress,
					inetSocketAddressSupplier);
			this.holder = new SearchHolder(comparator, anticipantion);
		}

		Collection<NetworkID> get(Collection<NetworkID> base) {
			search(nid -> new DHTSearch.SearchTask(() -> holder.update(nid, search(nid).getValue())), base);
			return holder.getResult();
		}
	}

	static class SearchResource extends DHTSearch {
		private final SearchHolder holder;
		private final Set<InetSocketAddress> result = new HashSet<>();
		private final int anticipantion;

		@Override
		boolean sufficient() {
			return result.size() >= anticipantion;
		}

		SearchResource(DHTAddress searchFor, NetworkSearch searcher, int anticipantion, int concurrencyLevel,
				Consumer<NetworkID> confirmNetworkID, Consumer<InetSocketAddress> rejectInetSocketAddress,
				Supplier<Collection<InetSocketAddress>> inetSocketAddressSupplier) {
			super(searchFor, searcher, concurrencyLevel, confirmNetworkID, rejectInetSocketAddress,
					inetSocketAddressSupplier);
			this.holder = new SearchHolder(comparator, anticipantion);
			this.anticipantion = anticipantion;
		}

		Collection<InetSocketAddress> get(Collection<NetworkID> base) {
			search(nid -> new DHTSearch.SearchTask(() -> {
				synchronized (result) {
					if (sufficient())
						return Collections.emptyList();
				}
				Pair<Boolean, Collection<NetworkID>> pair = search(nid);
				if (pair.getKey())
					synchronized (result) {
						result.add(nid.getInetSocketAddress());
						if (sufficient())
							return Collections.emptyList();
					}
				return holder.update(nid, pair.getValue());
			}), base);
			return result;
		}
	}
}