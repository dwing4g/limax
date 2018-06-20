package limax.key;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import limax.util.ConcurrentEnvironment;

class ServerEvaluate {
	private static final long DOMAIN_UPDATE = Long.getLong("limax.key.ServerEvaluate.DOMAIN_UPDATE", 300000);
	private static final int DYNAMIC_SERVERS = Integer.getInteger("limax.key.ServerEvaluate.DYNAMIC_SERVERS", 8);
	private static final int MAX_DYNAMIC_SERVERS = DYNAMIC_SERVERS * 2;
	private static final ScheduledThreadPoolExecutor scheduler = ConcurrentEnvironment.getInstance()
			.newScheduledThreadPool("limax.key.ServerEvaluate", 2, true);
	private volatile Set<InetAddress> priorityLower = Collections.emptySet();
	private volatile Set<InetAddress> priorityMedium = Collections.emptySet();
	private volatile Map<InetAddress, Long> priorityHigh = new ConcurrentHashMap<>();
	private String staticServer;

	ServerEvaluate(String initServer) throws UnknownHostException {
		priorityLower = Arrays.stream(InetAddress.getAllByName(initServer)).collect(Collectors.toSet());
		scheduler.scheduleAtFixedRate(() -> {
			try {
				priorityLower = Arrays.stream(InetAddress.getAllByName(initServer)).collect(Collectors.toSet());
			} catch (Exception e) {
			}
			try {
				priorityMedium = Arrays.stream(InetAddress.getAllByName(staticServer)).collect(Collectors.toSet());
			} catch (Exception e) {
			}
		}, DOMAIN_UPDATE, DOMAIN_UPDATE, TimeUnit.MILLISECONDS);
	}

	void addStaticServer(String staticServer) throws UnknownHostException {
		this.staticServer = staticServer;
		priorityMedium = Arrays.stream(InetAddress.getAllByName(staticServer)).collect(Collectors.toSet());
	}

	void addDynamicServer(Collection<InetAddress> randomServers, int timeout) {
		Set<InetAddress> priorityLower = this.priorityLower;
		Set<InetAddress> priorityMedium = this.priorityMedium;
		scheduler.execute(() -> {
			randomServers.stream().filter(
					inetAddress -> !priorityLower.contains(inetAddress) && !priorityMedium.contains(inetAddress))
					.forEach(inetAddress -> {
						try (Socket s = new Socket()) {
							long start = System.currentTimeMillis();
							s.connect(new InetSocketAddress(inetAddress, 443), timeout);
							priorityHigh.put(inetAddress, System.currentTimeMillis() - start);
						} catch (Exception e) {
						}
					});
			if (priorityHigh.size() > MAX_DYNAMIC_SERVERS) {
				priorityHigh = priorityHigh.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getValue))
						.limit(MAX_DYNAMIC_SERVERS)
						.collect(Collectors.toConcurrentMap(Map.Entry::getKey, Map.Entry::getValue));
			}
		});
	}

	InetAddress[] servers() {
		Set<InetAddress> priorityLower = this.priorityLower;
		Set<InetAddress> priorityMedium = this.priorityMedium;
		return Stream
				.concat(priorityHigh.entrySet().stream()
						.filter(e -> !priorityLower.contains(e.getKey()) && !priorityLower.contains(e.getKey()))
						.sorted(Comparator.comparing(Map.Entry::getValue)).map(Map.Entry::getKey)
						.limit(DYNAMIC_SERVERS), Stream.concat(priorityMedium.stream(), priorityLower.stream()))
				.toArray(InetAddress[]::new);
	}

	void drop(InetAddress inetAddress) {
		priorityHigh.remove(inetAddress);
	}
}
