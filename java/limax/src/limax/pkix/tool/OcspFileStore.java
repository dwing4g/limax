package limax.pkix.tool;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import limax.util.Trace;

class OcspFileStore {
	private final Path ocspStore;
	private final OcspServer ocspServer;
	private final ScheduledExecutorService scheduler;
	private final WatchService watchService;
	private final WatchKey ocspStoreKey;
	private final Map<X509Certificate, WatchKey> caStoreKeys = new HashMap<>();

	OcspFileStore(Path ocspStore, OcspServer ocspServer, ScheduledExecutorService scheduler) throws IOException {
		this.ocspStore = ocspStore;
		this.ocspServer = ocspServer;
		this.scheduler = scheduler;
		Files.createDirectories(ocspStore);
		this.watchService = ocspStore.getFileSystem().newWatchService();
		this.ocspStoreKey = ocspStore.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
				StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY,
				StandardWatchEventKinds.OVERFLOW);
	}

	Map<BigInteger, Long> addCA(X509Certificate cacert) throws IOException {
		Map<BigInteger, Long> revokes = new ConcurrentHashMap<>();
		long now = System.currentTimeMillis();
		Path caStore = ocspStore.resolve(cacert.getSerialNumber().toString(16));
		Files.createDirectories(caStore);
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(caStore, "*.cer")) {
			stream.forEach(path -> {
				try {
					String[] p = path.getFileName().toString().split("\\.");
					BigInteger serialNumber = new BigInteger(p[0], 16);
					long delta = Long.parseLong(p[1], 16) - now;
					long revocationTime = Long.parseUnsignedLong(p[2], 16);
					if (delta > 0) {
						revokes.put(serialNumber, revocationTime);
						scheduler.schedule(() -> {
							revokes.remove(serialNumber);
							Files.delete(path);
							return Void.TYPE;
						}, delta, TimeUnit.MILLISECONDS);
						return;
					} else {
						if (Trace.isInfoEnabled())
							Trace.info("OcspFileStore scanner, drop expire file " + path);
					}
				} catch (Exception e) {
					if (Trace.isWarnEnabled())
						Trace.warn("OcspFileStore scanner drop illegal file " + path, e);
				}
				try {
					Files.delete(path);
				} catch (Exception e) {
				}
			});
		}
		caStoreKeys.put(cacert, caStore.register(watchService, StandardWatchEventKinds.ENTRY_DELETE));
		return revokes;
	}

	void dropCA(X509Certificate cacert) {
		String serialNumber = cacert.getSerialNumber().toString(16);
		if (Trace.isInfoEnabled())
			Trace.info("OcspServer drop CAStore [" + serialNumber + "]");
		caStoreKeys.remove(cacert).cancel();
		try {
			Files.walkFileTree(ocspStore.resolve(serialNumber), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
					if (e == null) {
						Files.delete(dir);
						return FileVisitResult.CONTINUE;
					} else {
						throw e;
					}
				}
			});
		} catch (IOException e) {
			if (Trace.isWarnEnabled())
				Trace.warn("OcspServer drop CAStore [" + serialNumber + "]", e);
		}
	}

	void addRevoked(X509Certificate cacert, X509Certificate cert, long revocationTime) throws Exception {
		long notAfter = cert.getNotAfter().getTime();
		Path path = ocspStore.resolve(cacert.getSerialNumber().toString(16)).resolve(cert.getSerialNumber().toString(16)
				+ "." + Long.toHexString(notAfter) + "." + Long.toHexString(revocationTime) + ".cer");
		Files.write(path, cert.getEncoded());
		scheduler.schedule(() -> {
			Files.delete(path);
			return Void.TYPE;
		}, notAfter - revocationTime, TimeUnit.MILLISECONDS);
	}

	void removeRevoked(X509Certificate cacert, X509Certificate cert) throws Exception {
		long notAfter = cert.getNotAfter().getTime();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(
				ocspStore.resolve(cacert.getSerialNumber().toString(16)),
				cert.getSerialNumber().toString(16) + "." + Long.toHexString(notAfter) + ".*.cer")) {
			stream.forEach(path -> {
				try {
					Files.delete(path);
				} catch (IOException e) {
				}
			});
		}
	}

	void start() throws Exception {
		scanOcspStore();
		scheduler.submit(() -> {
			watch();
			return Void.TYPE;
		});
	}

	private void watch() throws Exception {
		WatchKey watchKey = null;
		try {
			watchKey = watchService.take();
			if (watchKey == ocspStoreKey) {
				scanOcspStore();
				watchKey.pollEvents();
			} else {
				for (WatchEvent<?> event : watchKey.pollEvents()) {
					if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
						Path path = (Path) event.context();
						String filename = path.getFileName().toString();
						ocspServer.recall(new BigInteger(filename.substring(0, filename.indexOf(".")), 16));
						if (Trace.isInfoEnabled())
							Trace.info("OcspFileStore monitor recall " + path);
					}
				}
			}
		} finally {
			watchKey.reset();
			scheduler.submit(() -> {
				watch();
				return Void.TYPE;
			});
		}
	}

	private void scanOcspStore() throws Exception {
		CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
		try (Stream<Path> stream = Files.list(ocspStore).filter(Files::isRegularFile)) {
			stream.forEach(path -> {
				if (Trace.isInfoEnabled())
					Trace.info("OcspFileStore monitor detect " + path);
				try (InputStream in = Files.newInputStream(path)) {
					for (Certificate cert : certificateFactory.generateCertificates(in)) {
						String prefix = "OcspFileStore monitor revoke "
								+ ((X509Certificate) cert).getSerialNumber().toString(16);
						try {
							ocspServer.revoke((X509Certificate) cert);
							if (Trace.isInfoEnabled())
								Trace.info(prefix + " [OK]");
						} catch (Exception e) {
							if (Trace.isWarnEnabled())
								Trace.warn(prefix + " [" + e.getMessage() + "]");
						}
					}
				} catch (Exception e) {
				}
				if (Trace.isInfoEnabled())
					Trace.info("OcspFileStore monitor remove " + path);
				try {
					Files.delete(path);
				} catch (Exception e) {
				}
			});
		}
	}
}
