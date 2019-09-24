package limax.pkix.tool;

import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.CRL;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.security.auth.x500.X500Principal;

import limax.net.Engine;
import limax.pkix.CAService;
import limax.pkix.ExtKeyUsage;
import limax.pkix.GeneralName;
import limax.pkix.KeyInfo;
import limax.pkix.KeyUsage;
import limax.pkix.X509CACertificateParameter;
import limax.pkix.X509CRLParameter;
import limax.pkix.X509EndEntityCertificateParameter;
import limax.pkix.X509RootCertificateParameter;
import limax.util.ConcurrentEnvironment;
import limax.util.ElementHelper;
import limax.util.SecurityUtils;
import limax.util.SecurityUtils.PublicKeyAlgorithm;
import limax.util.Trace;

public class Main {
	private Main() {
	}

	private static Date parse(String s) {
		return new Date(LocalDate.from(DateTimeFormatter.BASIC_ISO_DATE.parse(s)).atStartOfDay(ZoneId.systemDefault())
				.toInstant().toEpochMilli());
	}

	private static void u(String message) {
		System.out.println("Usage: java -jar limax.jar pkix " + message);
	}

	private final static String[] USAGES = new String[] { "algo", "keygen <algo> <PathPrefix>",
			"copy <locationSRC> <locationDST>", "initroot <locationROOT> <subject> <yyyyMMdd> <yyyyMMdd>",
			"initca <locationROOT> (<locationCA>|<keygen/.pub>) <subject> <yyyyMMdd> <yyyyMMdd> <OcspDomainOfRoot>",
			"initlmkca <locationROOT> (<locationCA>|<keygen/.pub>) <subject> <domain> <yyyyMMdd> <yyyyMMdd> <OcspDomainOfRoot>",
			"initocsp <location> <locationOcsp> <subject> <yyyyMMdd> <yyyyMMdd>",
			"gencrl <location> <crlfile> <nextUpdate(yyyyMMdd)> [[-](CertDir|CertFile)]", "ca [path to caserver.xml]",
			"lmk [path to lmkserver.xml]", "ocsp [path to ocspserver.xml]" };

	private static void usage(int index) {
		if (index == -1) {
			for (int i = 0; i < USAGES.length; i++)
				u(USAGES[i]);
		} else
			u(USAGES[index]);
		System.exit(0);
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 1)
			usage(-1);
		String cmd = args[0].toUpperCase();
		args = Arrays.copyOfRange(args, 1, args.length);
		switch (cmd) {
		case "ALGO":
			mainAlgo(args);
			break;
		case "KEYGEN":
			mainKeygen(args);
			break;
		case "COPY":
			mainCopy(args);
			break;
		case "INITROOT":
			mainInitroot(args);
			break;
		case "INITCA":
			mainInitca(args, signCA);
			break;
		case "INITLMKCA":
			mainInitca(args, signLmkCA);
			break;
		case "INITOCSP":
			mainInitocsp(args);
			break;
		case "GENCRL":
			mainGencrl(args);
			break;
		case "CA":
			Engine.open(4, 16, 64);
			mainCa(args);
			break;
		case "LMK":
			Engine.open(4, 16, 64);
			mainLmk(args);
			break;
		case "OCSP":
			Engine.open(4, 16, 64);
			mainOcsp(args);
			break;
		default:
			usage(-1);
		}
	}

	private static void mainAlgo(String[] args) throws Exception {
		System.out.println("Supported Algorithm:\n");
		CAService.getAlgorithms().stream().sorted().forEach(name -> System.out.println("\t" + name));
	}

	private static void mainKeygen(String[] args) throws Exception {
		if (args.length != 2)
			usage(1);
		String[] part = args[0].split("/");
		KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(part[0]);
		keyPairGenerator.initialize(Integer.parseInt(part[1]));
		KeyPair keyPair = keyPairGenerator.generateKeyPair();
		Path path = Paths.get(args[1]).toAbsolutePath();
		String prefix = path.getFileName().toString();
		path = path.getParent();
		Path pathKey = path.resolve(prefix + ".key");
		Path pathPub = path.resolve(prefix + ".pub");
		char[] passphrase = getPassphrase("KeyFile [" + pathKey + "] Password:");
		if (!Arrays.equals(passphrase, getPassphrase("Confirm KeyFile [" + pathKey + "] Password:")))
			throw new RuntimeException("Differ Password Input.");
		Files.write(pathKey, SecurityUtils.assemblePKCS8(keyPair.getPrivate(), passphrase).getBytes());
		Files.write(pathPub, SecurityUtils.assembleX509PublicKey(keyPair.getPublic()).getBytes());
	}

	private static void mainCopy(String args[]) throws Exception {
		if (args.length != 2)
			usage(2);
		KeyInfo keyInfo = KeyInfo.load(URI.create(args[0]), Main::getPassphrase);
		KeyInfo.save(URI.create(args[1]), keyInfo.getPrivateKey(), keyInfo.getCertificateChain(), Main::getPassphrase);
	}

	private static void mainInitroot(String[] args) throws Exception {
		if (args.length != 4)
			usage(3);
		URI location = URI.create(args[0]);
		X500Principal subject = new X500Principal(args[1]);
		Date notBefore = parse(args[2]);
		Date notAfter = parse(args[3]);
		CAService.create(location, new X509RootCertificateParameter() {
			@Override
			public X500Principal getSubject() {
				return subject;
			}

			@Override
			public Date getNotBefore() {
				return notBefore;
			}

			@Override
			public Date getNotAfter() {
				return notAfter;
			}
		}, Main::getPassphrase);
	}

	private static void mainInitca(String[] args, SignCA signca) throws Exception {
		if (signca == signCA && args.length != 6)
			usage(4);
		if (signca == signLmkCA && args.length != 7)
			usage(5);
		CAService ca = CAService.create(URI.create(args[0]), Main::getPassphrase);
		try {
			Path pathPublicKey = Paths.get(args[1]);
			if (Files.exists(pathPublicKey)) {
				String filename = pathPublicKey.getFileName().toString();
				int pos = filename.lastIndexOf('.');
				filename = (pos == -1 ? filename : filename.substring(0, pos)) + ".p7b";
				String pkcs7 = SecurityUtils.assemblePKCS7(signca.sign(args, ca,
						PublicKeyAlgorithm.loadPublicKey(new String(Files.readAllBytes(pathPublicKey)))));
				Files.write(pathPublicKey.resolveSibling(filename), pkcs7.getBytes());
				return;
			}
		} catch (Exception e) {
		}
		URI location = URI.create(args[1]);
		KeyPair keyPair = keyPairGenerator(ca, location);
		KeyInfo.save(location, keyPair.getPrivate(), signca.sign(args, ca, keyPair.getPublic()), Main::getPassphrase);
	}

	private static void mainInitocsp(String[] args) throws Exception {
		if (args.length != 5)
			usage(6);
		CAService ca = CAService.create(URI.create(args[0]), Main::getPassphrase);
		URI location = URI.create(args[1]);
		X500Principal subject = new X500Principal(args[2]);
		Date notBefore = parse(args[3]);
		Date notAfter = parse(args[4]);
		KeyPair keyPair = keyPairGenerator(ca, location);
		KeyInfo.save(location, keyPair.getPrivate(), ca.sign(new X509EndEntityCertificateParameter() {
			@Override
			public X500Principal getSubject() {
				return subject;
			}

			@Override
			public Date getNotBefore() {
				return notBefore;
			}

			@Override
			public Date getNotAfter() {
				return notAfter;
			}

			@Override
			public URI getOcspURI() {
				return null;
			}

			@Override
			public Function<X509Certificate, URI> getCRLDPMapping() {
				return null;
			}

			@Override
			public PublicKey getPublicKey() {
				return keyPair.getPublic();
			}

			@Override
			public EnumSet<KeyUsage> getKeyUsages() {
				return EnumSet.of(KeyUsage.digitalSignature);
			}

			@Override
			public EnumSet<ExtKeyUsage> getExtKeyUsages() {
				return EnumSet.of(ExtKeyUsage.OCSPSigning);
			}
		}), Main::getPassphrase);
	}

	private static void mainGencrl(String[] args) throws Exception {
		if (args.length != 3 && args.length != 4)
			usage(7);
		CAService ca = CAService.create(URI.create(args[0]), Main::getPassphrase);
		X509Certificate cacert = ca.getCACertificate();
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		long now = System.currentTimeMillis();
		long nextUpdateDelay = parse(args[2]).getTime() - now;
		Map<BigInteger, Long> revokes = new HashMap<>();
		Path crlPath = Paths.get(args[1]);
		if (Files.isRegularFile(crlPath)) {
			try (InputStream in = Files.newInputStream(crlPath)) {
				for (CRL crl : cf.generateCRLs(in)) {
					X509CRL x509crl = (X509CRL) crl;
					x509crl.verify(ca.getCACertificate().getPublicKey());
					Set<? extends X509CRLEntry> entries = x509crl.getRevokedCertificates();
					if (entries != null)
						entries.forEach(e -> revokes.put(e.getSerialNumber(), e.getRevocationDate().getTime()));
				}
			}
		}
		if (args.length == 4) {
			Path path = Paths.get(args[3]);
			boolean revoke;
			if (Files.notExists(path) && args[3].startsWith("-")) {
				path = Paths.get(args[3].substring(1));
				revoke = false;
			} else {
				revoke = true;
			}
			try (Stream<Path> stream = (Files.isDirectory(path) ? Files.list(path) : Stream.of(path))
					.filter(Files::isRegularFile)) {
				stream.forEach(p -> {
					String filename = p.getFileName().toString();
					try (InputStream in = Files.newInputStream(p)) {
						for (X509Certificate cert : cf.generateCertificates(in).toArray(new X509Certificate[0])) {
							if (cert.equals(cacert)) {
								if (revoke)
									System.out.println("process <" + filename + "> [error: revoke CA self]");
							} else if (SecurityUtils.isSignedBy(cert, cacert)) {
								if (revoke)
									System.out.println("process <" + filename + "> ["
											+ (revokes.putIfAbsent(cert.getSerialNumber(), now) == null ? "OK"
													: "error: repetitive revoke.")
											+ "]");
								else
									System.out.println("process <" + filename + "> ["
											+ (revokes.remove(cert.getSerialNumber()) != null ? "OK"
													: "error: not revoked.")
											+ "]");
							} else {
								System.out.println("process <" + filename + "> [error: not Signed by the CA]");
							}
						}
					} catch (Exception e) {
						System.out.println("process <" + filename + "> [exception: " + e.getMessage() + "]");
					}
				});
			}
		}
		Files.write(crlPath, ca.sign(new X509CRLParameter() {
			@Override
			public X509Certificate getCACertificate() {
				return cacert;
			}

			@Override
			public Map<BigInteger, Long> getRevokes() {
				return revokes;
			}

			@Override
			public long getNextUpdateDelay() {
				return nextUpdateDelay > 0 ? nextUpdateDelay : TimeUnit.DAYS.toMillis(30);
			}
		}).getEncoded());
	}

	private static void mainCa(String[] args) throws Exception {
		Path path = Paths.get(args.length == 0 ? "caserver.xml" : args[0]);
		if (!Files.isReadable(path))
			usage(8);
		new CAServer(new ServerConfig(path)).start();
	}

	private static void mainLmk(String[] args) throws Exception {
		Path path = Paths.get(args.length == 0 ? "lmkserver.xml" : args[0]);
		if (!Files.isReadable(path))
			usage(9);
		new LmkServer(new ServerConfig(path)).start();
	}

	private static void mainOcsp(String[] args) throws Exception {
		Path path = Paths.get(args.length == 0 ? "ocspserver.xml" : args[0]);
		if (!Files.isReadable(path))
			usage(10);
		ServerConfig serverConfig = new ServerConfig(path);
		ElementHelper eh = new ElementHelper(serverConfig.getRoot());
		URI location = URI.create(eh.getString("location"));
		String passphrase = eh.getString("passphrase", null);
		if (passphrase != null && Trace.isWarnEnabled())
			Trace.warn("OcspServer " + location + " passphrase SHOULD NOT contains in config file, except for test.");
		KeyInfo keyInfo = KeyInfo.load(location,
				passphrase == null ? Main::getPassphrase : prompt -> passphrase.toCharArray());
		ScheduledExecutorService scheduler = ConcurrentEnvironment.getInstance()
				.newScheduledThreadPool("OcspServer Scheduler", 3);
		serverConfig.createOcspServer(keyInfo, serverConfig.getDomain(), scheduler).start();
	}

	private static char[] getPassphrase(String prompt) {
		return System.console().readPassword(prompt);
	}

	static KeyPair keyPairGenerator(CAService ca, String algo) throws Exception {
		try {
			String[] part = algo.split("/");
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(part[0]);
			keyPairGenerator.initialize(Integer.parseInt(part[1]));
			return keyPairGenerator.generateKeyPair();
		} catch (Exception e) {
		}
		PublicKey publicKey = ca.getCACertificate().getPublicKey();
		return SecurityUtils.PublicKeyAlgorithm.valueOf(publicKey).reKey(publicKey);
	}

	private static KeyPair keyPairGenerator(CAService ca, URI location) throws Exception {
		return keyPairGenerator(ca, location.getFragment());
	}

	private interface SignCA {
		X509Certificate[] sign(String[] args, CAService ca, PublicKey publicKey) throws Exception;
	}

	private final static SignCA signCA = (args, ca, publicKey) -> {
		X500Principal subject = new X500Principal(args[2]);
		Date notBefore = parse(args[3]);
		Date notAfter = parse(args[4]);
		URI baseURI = new URI("http", args[5], "/", null);
		return ca.sign(new X509CACertificateParameter() {
			@Override
			public X500Principal getSubject() {
				return subject;
			}

			@Override
			public Date getNotBefore() {
				return notBefore;
			}

			@Override
			public Date getNotAfter() {
				return notAfter;
			}

			@Override
			public PublicKey getPublicKey() {
				return publicKey;
			}

			@Override
			public URI getOcspURI() {
				return baseURI.resolve("ocsp");
			}

			@Override
			public Function<X509Certificate, URI> getCRLDPMapping() {
				return cacert -> baseURI.resolve("crl/").resolve(cacert.getSerialNumber().toString(16) + ".crl");
			}
		});
	};

	private final static SignCA signLmkCA = (args, ca, publicKey) -> {
		X500Principal subject = new X500Principal(args[2]);
		GeneralName dnsName = GeneralName.createDNSName(args[3].toLowerCase());
		Date notBefore = parse(args[4]);
		Date notAfter = parse(args[5]);
		URI baseURI = new URI("http", args[6], "/", null);
		return ca.sign(new X509CACertificateParameter() {
			@Override
			public X500Principal getSubject() {
				return subject;
			}

			@Override
			public Date getNotBefore() {
				return notBefore;
			}

			@Override
			public Date getNotAfter() {
				return notAfter;
			}

			@Override
			public PublicKey getPublicKey() {
				return publicKey;
			}

			@Override
			public URI getOcspURI() {
				return baseURI.resolve("ocsp");
			}

			@Override
			public Function<X509Certificate, URI> getCRLDPMapping() {
				return cacert -> baseURI.resolve("crl/").resolve(cacert.getSerialNumber().toString(16) + ".crl");
			}

			@Override
			public Collection<GeneralName> getSubjectAltNames() {
				return Arrays.asList(dnsName);
			}
		});
	};
}