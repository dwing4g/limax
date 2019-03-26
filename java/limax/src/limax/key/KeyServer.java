package limax.key;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.w3c.dom.Element;

import limax.http.HttpServer;
import limax.p2p.Neighbors;
import limax.pkix.SSLContextAllocator;
import limax.util.ConcurrentEnvironment;
import limax.util.ElementHelper;
import limax.util.Trace;

class KeyServer {
	private static final int SHUTDOWN_DELAY_SECOND = 1;
	private static final long NEIGHBORS_SAVE_PERIOD = Long.getLong("limax.key.KeyServer.NEIGHBORS_SAVE_PERIOD", 300000);
	private static final long NEIGHBORS_REFRESH_PERIOD = Long.getLong("limax.key.KeyServer.NEIGHBORS_REFRESH_PERIOD",
			600000);
	static final int NETWORK_TIMEOUT = Integer.getInteger("limax.key.KeyServer.NETWORK_TIMEOUT", 3000);

	private static final ThreadPoolExecutor executor = ConcurrentEnvironment.getInstance()
			.newThreadPool("limax.key.keyServer", 0, true);
	private static final ScheduledExecutorService scheduler = ConcurrentEnvironment.getInstance()
			.newScheduledThreadPool("limax.key.KeyServer.scheduler", 16);
	private final SSLContextAllocator sslContextAllocator;
	private final MasterKeyContainer masterKeyContainer;
	private final Neighbors neighbors;
	private final P2pHandler p2pHandler;
	private final KeyHandler keyHandler;
	private final long publishPeriod;
	private HttpServer server;

	KeyServer(Path configPath, Element root) throws Exception {
		ElementHelper eh = new ElementHelper((Element) root.getElementsByTagName("Trace").item(0));
		Trace.Config config = new Trace.Config();
		config.setOutDir(eh.getString("outDir", "./trace"));
		config.setConsole(eh.getBoolean("console", true));
		config.setRotateHourOfDay(eh.getInt("rotateHourOfDay", 6));
		config.setRotateMinute(eh.getInt("rotateMinute", 0));
		config.setRotatePeriod(eh.getLong("rotatePeriod", 86400000l));
		config.setLevel(eh.getString("level", "warn").toUpperCase());
		Trace.openNew(config);
		eh = new ElementHelper(root);
		URI location = URI.create(eh.getString("location"));
		String passphrase = eh.getString("passphrase", null);
		if (passphrase != null && Trace.isWarnEnabled()) {
			Trace.warn("KeyServer " + location + " passphrase SHOULD NOT contains in config file, except for test.");
			this.sslContextAllocator = new SSLContextAllocator(location, prompt -> passphrase.toCharArray());
		} else
			this.sslContextAllocator = new SSLContextAllocator(location,
					prompt -> System.console().readPassword(prompt));
		this.sslContextAllocator.addChangeListener(_sa -> {
			try {
				start();
			} catch (Exception e) {
			}
		});
		this.sslContextAllocator.getTrustManager()
				.setRevocationCheckerOptions(eh.getString("revocationCheckerOptions"));
		String trustsPath = eh.getString("trustsPath", null);
		if (trustsPath != null)
			this.sslContextAllocator.getTrustManager().addTrust(Paths.get(trustsPath));
		String algorithm = eh.getString("algorithm", "sha-256");
		MessageDigest.getInstance(algorithm).clone();
		String[] master = eh.getString("master").split(",");
		Path path = configPath.resolve("keyserver.dht");
		byte[] configData = null;
		try {
			configData = Files.readAllBytes(path);
		} catch (Exception e) {
		}
		this.neighbors = new Neighbors(scheduler, () -> {
			return Arrays.stream(master).flatMap(host -> {
				try {
					return Arrays.stream(InetAddress.getAllByName(host));
				} catch (UnknownHostException e) {
					return Stream.empty();
				}
			}).map(inetAddress -> new InetSocketAddress(inetAddress, 443)).collect(Collectors.toList());
		}, inetSocketAddress -> {
			try (Socket s = new Socket()) {
				s.connect(inetSocketAddress, NETWORK_TIMEOUT);
				return true;
			} catch (Exception e) {
				return false;

			}
		}, configData);
		if (Trace.isInfoEnabled())
			Trace.info("Neighbors init " + neighbors.size() + " entries.");
		scheduler.scheduleAtFixedRate(() -> {
			try {
				Files.write(path, neighbors.encode());
				if (Trace.isInfoEnabled())
					Trace.info("Neighbors save " + neighbors.size() + " entries.");
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("Neighbors save entries.", e);
			}
		}, 0, NEIGHBORS_SAVE_PERIOD, TimeUnit.MILLISECONDS);
		this.masterKeyContainer = new MasterKeyContainer(scheduler, algorithm,
				TimeUnit.DAYS.toMillis(eh.getLong("keyLifespan", 365)));
		this.p2pHandler = new P2pHandler(sslContextAllocator, neighbors, masterKeyContainer, executor);
		this.keyHandler = new KeyHandler(masterKeyContainer);
		this.publishPeriod = eh.getLong("publishPeriod", -1);
	}

	synchronized void start() throws Exception {
		boolean first;
		if (server == null) {
			first = true;
		} else {
			first = false;
			server.stop();
			Thread.sleep(SHUTDOWN_DELAY_SECOND);
		}
		server = HttpServer.create(new InetSocketAddress(443), sslContextAllocator.alloc(), false, true);
		server.createContext("/", keyHandler);
		p2pHandler.createContext(server);
		server.start();
		if (first) {
			masterKeyContainer.setRandomServers(p2pHandler.refresh());
			masterKeyContainer.start(publishPeriod, timestamp -> p2pHandler.publish(timestamp));
			scheduler.scheduleWithFixedDelay(() -> masterKeyContainer.setRandomServers(p2pHandler.refresh()),
					NEIGHBORS_REFRESH_PERIOD, NEIGHBORS_REFRESH_PERIOD, TimeUnit.MILLISECONDS);
		}
	}
}
