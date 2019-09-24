package limax.pkix.tool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.security.auth.x500.X500Principal;

import org.w3c.dom.Element;

import limax.codec.JSON;
import limax.codec.JSONException;
import limax.codec.Octets;
import limax.codec.RFC2822Address;
import limax.codec.SHA1;
import limax.codec.SinkOctets;
import limax.codec.StreamSource;
import limax.codec.asn1.ASN1ObjectIdentifier;
import limax.http.DataSupplier;
import limax.http.Headers;
import limax.http.HttpException;
import limax.http.HttpExchange;
import limax.http.HttpHandler;
import limax.http.HttpServer;
import limax.pkix.CAService;
import limax.pkix.ExtKeyUsage;
import limax.pkix.GeneralName;
import limax.pkix.KeyUsage;
import limax.pkix.X509EndEntityCertificateParameter;
import limax.util.ElementHelper;
import limax.util.Helper;
import limax.util.SecurityUtils;
import limax.util.Trace;
import limax.util.XMLUtils;

class CertServer {
	static final long CERTFILE_MAX_SIZE = Long.getLong("limax.pkix.tool.CertServer.CERTFILE_SIZE_MAX", 65536);
	private static final long CACHE_TIME_OUT = 30000;
	private static final int MASK_USAGE_MANDATORY = 2;
	private static final int MASK_USAGE_TRUE = 1;
	private final CAService ca;
	private final Instant caLifetime;
	private final int port;
	private final OcspServer ocspServer;
	private final AuthCode authCode;
	private final Archive archive;
	private final KeyPairGenerator privateKeyGenerator;
	private final Pattern subjectPattern;
	private final String subjectTemplate;
	private final boolean notBeforeMandatory;
	private final boolean notAfterMandatory;
	private final long notAfterPeriod;
	private final long notAfterPeriodLow;
	private final long notAfterPeriodHigh;
	private final Map<KeyUsage, Integer> keyUsage;
	private final Map<ExtKeyUsage, Integer> extKeyUsage;

	private static class DownloadCache {
		private static final Map<String, DownloadCache> cache = new ConcurrentHashMap<>();
		private static final Timer timer;
		private final String key;
		private final byte[] data;
		private final long timestamp;
		static {
			timer = new Timer(true);
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					long now = System.currentTimeMillis();
					for (Iterator<Map.Entry<String, DownloadCache>> it = cache.entrySet().iterator(); it.hasNext();) {
						if (it.next().getValue().timestamp < now)
							it.remove();
					}
				}
			}, CACHE_TIME_OUT, CACHE_TIME_OUT);
		}

		DownloadCache(String suffix, byte[] data) {
			this.key = Helper.toHexString(SHA1.digest(data)) + suffix;
			this.data = data;
			this.timestamp = System.currentTimeMillis();
			cache.put(this.key, this);
		}

		static DownloadCache get(String key) {
			return cache.get(key);
		}
	}

	class Handler implements HttpHandler {
		@Override
		public DataSupplier handle(HttpExchange exchange) throws Exception {
			String path = exchange.getRequestURI().getPath();
			Headers headers = exchange.getResponseHeaders();
			if (path.length() > 1) {
				DownloadCache c = DownloadCache.get(path.substring(1));
				if (c != null) {
					headers.set("Content-Type", Files.probeContentType(Paths.get(c.key)));
					headers.set("Cache-Control", "no-store");
					return DataSupplier.from(c.data);
				}
			}
			headers.set("Location", "/CertServer.html");
			headers.set(":status", HttpURLConnection.HTTP_MOVED_PERM);
			return null;
		}
	}

	private static JSON requestJSON(HttpExchange exchange) throws JSONException {
		return JSON.parse(exchange.getRequestURI().getQuery());
	}

	private static DataSupplier responseJSON(HttpExchange exchange, Object obj) throws JSONException {
		Headers headers = exchange.getResponseHeaders();
		headers.set("Content-Type", "application/json");
		headers.set("Cache-Control", "no-store");
		return DataSupplier.from(JSON.stringify(obj).getBytes());
	}

	private X500Principal getSubject(JSON json, Map<String, Object> transform) {
		try {
			String subject = ((JSON) json.get("subject")).toString();
			if (!subject.isEmpty() && subjectPattern.matcher(subject).matches()) {
				X500Principal principal = new X500Principal(subject);
				transform.put("subject", principal.toString());
				return principal;
			}
		} catch (Exception e) {
		}
		transform.put("subject", null);
		return null;
	}

	private static Collection<GeneralName> getSubjectAltNames(JSON json, Map<String, Object> transform) {
		try {
			JSON list = json.get("subjectAltNames");
			if (list.isUndefined())
				return Collections.emptyList();
			List<GeneralName> subjectAltNames = new ArrayList<>();
			List<Object> results = new ArrayList<>();
			for (JSON item : list.toArray()) {
				try {
					String key = item.keySet().iterator().next();
					String val = item.get(key).toString();
					if (val.isEmpty())
						throw new RuntimeException();
					Object obj;
					switch (GeneralName.Type.valueOf(key)) {
					case rfc822Name:
						RFC2822Address address = new RFC2822Address(val);
						subjectAltNames.add(GeneralName.createRFC822Name(address));
						obj = address;
						break;
					case dNSName:
						subjectAltNames.add(GeneralName.createDNSName(val));
						obj = val;
						break;
					case directoryName:
						X500Principal name = new X500Principal(val);
						subjectAltNames.add(GeneralName.createDirectoryName(name));
						obj = name;
						break;
					case uniformResourceIdentifier:
						URI uri = new URI(val);
						subjectAltNames.add(GeneralName.createUniformResourceIdentifier(uri));
						obj = uri;
						break;
					case iPAddress:
						InetAddress ip = InetAddress.getByName(val);
						subjectAltNames.add(GeneralName.createIPAddress(ip));
						obj = ip.getHostAddress();
						break;
					case registeredID:
						subjectAltNames.add(GeneralName.createRegisteredID(val));
						obj = new ASN1ObjectIdentifier(val);
						break;
					default:
						throw new RuntimeException();
					}
					results.add(obj.toString());
				} catch (Exception e) {
					results.add(null);
				}
			}
			transform.put("subjectAltNames", results);
			return subjectAltNames;
		} catch (JSONException e) {
		}
		return null;
	}

	private static String instant2LocalDateString(Instant instant) {
		return instant.atZone(ZoneId.systemDefault()).toLocalDate().toString();
	}

	private Instant getNotBefore(JSON json, Map<String, Object> transform) {
		try {
			String date = notBeforeMandatory ? instant2LocalDateString(Instant.now())
					: json.get("notBefore").toString();
			transform.put("notBefore", date);
			return LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant();
		} catch (Exception e) {
		}
		transform.put("notBefore", null);
		return null;
	}

	private Instant getNotAfter(JSON json, Instant notBefore, Map<String, Object> transform) {
		try {
			String date = notAfterMandatory ? instant2LocalDateString(notBefore.plusMillis(notAfterPeriod))
					: json.get("notAfter").toString();
			transform.put("notAfter", date);
			return LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant();
		} catch (Exception e) {
		}
		transform.put("notAfter", null);
		return null;
	}

	private EnumSet<KeyUsage> getKeyUsages(JSON json, Map<String, Object> transform) {
		try {
			List<KeyUsage> chosen = new ArrayList<>();
			for (Map.Entry<KeyUsage, Integer> e : keyUsage.entrySet())
				if (e.getValue() == (MASK_USAGE_MANDATORY | MASK_USAGE_TRUE))
					chosen.add(e.getKey());
			JSON list = json.get("keyUsage");
			if (!list.isUndefined()) {
				for (JSON item : list.toArray()) {
					try {
						KeyUsage usage = KeyUsage.valueOf(item.toString());
						if ((keyUsage.get(usage) & MASK_USAGE_MANDATORY) == 0)
							chosen.add(usage);
					} catch (Exception e) {
					}
				}
			}
			transform.put("keyUsage", chosen.stream().map(Object::toString).collect(Collectors.toList()));
			return chosen.isEmpty() ? EnumSet.noneOf(KeyUsage.class) : EnumSet.copyOf(chosen);
		} catch (JSONException e) {
		}
		return null;
	}

	private EnumSet<ExtKeyUsage> getExtKeyUsages(JSON json, Map<String, Object> transform) {
		try {
			List<ExtKeyUsage> chosen = new ArrayList<>();
			for (Map.Entry<ExtKeyUsage, Integer> e : extKeyUsage.entrySet())
				if (e.getValue() == (MASK_USAGE_MANDATORY | MASK_USAGE_TRUE))
					chosen.add(e.getKey());
			JSON list = json.get("extKeyUsage");
			if (!list.isUndefined()) {
				for (JSON item : list.toArray()) {
					try {
						ExtKeyUsage usage = ExtKeyUsage.valueOf(item.toString());
						if ((extKeyUsage.get(usage) & MASK_USAGE_MANDATORY) == 0)
							chosen.add(usage);
					} catch (Exception e) {
					}
				}
			}
			transform.put("extKeyUsage", chosen.stream().map(Object::toString).collect(Collectors.toList()));
			return chosen.isEmpty() ? EnumSet.noneOf(ExtKeyUsage.class) : EnumSet.copyOf(chosen);
		} catch (JSONException e) {
		}
		return null;
	}

	private static PublicKey getPublicKey(JSON json, Map<String, Object> transform) {
		try {
			JSON publicKey = json.get("publicKey");
			return publicKey.isUndefined() ? null
					: SecurityUtils.PublicKeyAlgorithm
							.loadPublicKey((X509EncodedKeySpec) SecurityUtils.loadPEM(publicKey.toString(), null));
		} catch (Exception e) {
		}
		transform.put("publicKey", null);
		return null;
	}

	private static char[] getPassphrase(JSON json) {
		try {
			JSON passphrase = json.get("pkcs12passphrase");
			if (passphrase.isString())
				return passphrase.toString().toCharArray();
		} catch (Exception e) {
		}
		return null;
	}

	private boolean verifyAuthCode(JSON json, Map<String, Object> transform) {
		try {
			if (authCode.verify(json.get("authCode").toString()))
				return true;
		} catch (Exception e) {
		}
		transform.put("authCode", null);
		return false;
	}

	private class HandlerSign implements HttpHandler {
		@Override
		public DataSupplier handle(HttpExchange exchange) throws JSONException {
			JSON json = requestJSON(exchange);
			if (json.isNull()) {
				Map<String, Object> initial = new HashMap<>();
				Instant now = Instant.now();
				initial.put("subject", subjectTemplate);
				initial.put("notBefore", Collections.singletonMap(instant2LocalDateString(now), notBeforeMandatory));
				initial.put("notAfter", instant2LocalDateString(now.plusMillis(notAfterPeriod)));
				initial.put("keyUsage", keyUsage);
				initial.put("extKeyUsage", extKeyUsage);
				return responseJSON(exchange, initial);
			}
			Map<String, Object> transform = new HashMap<>();
			X500Principal subject = getSubject(json, transform);
			Collection<GeneralName> subjectAltNames = getSubjectAltNames(json, transform);
			Instant notBefore = getNotBefore(json, transform);
			Instant notAfter = getNotAfter(json, notBefore, transform);
			if (notAfter != null && notBefore != null) {
				long delta = notAfter.toEpochMilli() - notBefore.toEpochMilli();
				if (delta < notAfterPeriodLow || delta > notAfterPeriodHigh)
					transform.put("notAfter", null);
				if (notAfter.isAfter(caLifetime)) {
					transform.put("notAfter", null);
					if (Trace.isErrorEnabled()) {
						Trace.error("CertServer CANNOT signature certificate notAfter = " + notAfter
								+ " but CA's lifetime = " + caLifetime);
					}
				}
			}
			EnumSet<KeyUsage> keyUsages = getKeyUsages(json, transform);
			EnumSet<ExtKeyUsage> extKeyUsages = getExtKeyUsages(json, transform);
			PublicKey publicKey = getPublicKey(json, transform);
			char[] passphrase = getPassphrase(json);
			if (verifyAuthCode(json, transform) && subject != null && subjectAltNames != null && notBefore != null
					&& notAfter != null && keyUsages != null && extKeyUsages != null
					&& (publicKey != null || passphrase != null)) {
				PublicKey _publicKey;
				PrivateKey _privateKey;
				if (publicKey == null) {
					KeyPair keyPair;
					synchronized (privateKeyGenerator) {
						keyPair = privateKeyGenerator.generateKeyPair();
					}
					_publicKey = keyPair.getPublic();
					_privateKey = keyPair.getPrivate();
				} else {
					_publicKey = publicKey;
					_privateKey = null;
				}
				try {
					X509Certificate[] chain = ca.sign(new X509EndEntityCertificateParameter() {
						@Override
						public X500Principal getSubject() {
							return subject;
						}

						@Override
						public Collection<GeneralName> getSubjectAltNames() {
							return subjectAltNames;
						}

						@Override
						public Date getNotBefore() {
							return new Date(notBefore.toEpochMilli());
						}

						@Override
						public Date getNotAfter() {
							return new Date(notAfter.toEpochMilli());
						}

						@Override
						public EnumSet<KeyUsage> getKeyUsages() {
							return keyUsages;
						}

						@Override
						public EnumSet<ExtKeyUsage> getExtKeyUsages() {
							return extKeyUsages;
						}

						@Override
						public PublicKey getPublicKey() {
							return _publicKey;
						}

						@Override
						public URI getOcspURI() {
							return ocspServer.getOcspURI();
						}

						@Override
						public Function<X509Certificate, URI> getCRLDPMapping() {
							return cacert -> ocspServer.getCRLDP(cacert);
						}
					});
					archive.store(chain[0]);
					DownloadCache cache;
					if (_privateKey == null) {
						cache = new DownloadCache(".p7b", SecurityUtils.assemblePKCS7(chain).getBytes());
					} else {
						ByteArrayOutputStream os = new ByteArrayOutputStream();
						KeyStore pkcs12 = KeyStore.getInstance("PKCS12");
						pkcs12.load(null, null);
						pkcs12.setKeyEntry("", _privateKey, passphrase, chain);
						pkcs12.store(os, passphrase);
						cache = new DownloadCache(".p12", os.toByteArray());
					}
					transform.put("retrieveKey", cache.key);
					if (Trace.isInfoEnabled())
						Trace.info("CertServer sign [" + subject.toString() + "]");
				} catch (Exception e) {
					if (Trace.isErrorEnabled())
						Trace.error("CertServer sign certificate", e);
				}
			}
			return responseJSON(exchange, transform);
		}
	}

	private interface X509CertificateConsumer {
		void accept(X509Certificate cert) throws Exception;
	}

	private class HandlerMaintance implements HttpHandler {
		private final String opname;
		private final X509CertificateConsumer consumer;

		HandlerMaintance(String op, X509CertificateConsumer consumer) {
			this.opname = op;
			this.consumer = consumer;
		}

		@Override
		public DataSupplier handle(HttpExchange exchange) throws JSONException {
			JSON json = requestJSON(exchange);
			Map<String, Object> transform = new HashMap<>();
			X509Certificate certificate = null;
			try {
				Collection<? extends Certificate> chain = CertificateFactory.getInstance("X.509")
						.generateCertificates(new ByteArrayInputStream(json.get("certificate").toString().getBytes()));
				certificate = chain.size() == 1 ? (X509Certificate) chain.iterator().next()
						: (X509Certificate) SecurityUtils.sortCertificateChain(chain.toArray(new Certificate[0]))[0];
			} catch (Exception e) {
				transform.put("certificate", null);
			}
			if (certificate != null && verifyAuthCode(json, transform)) {
				Map<String, Object> r = new HashMap<>();
				try {
					consumer.accept(certificate);
					r.put("status", true);
					r.put("message", "Certificate " + certificate.getSerialNumber().toString(16) + " " + opname);
					if (Trace.isInfoEnabled())
						Trace.info(
								"CertServer " + opname + " [" + certificate.getSubjectX500Principal().toString() + "]");
				} catch (Exception e) {
					r.put("status", false);
					r.put("message", e.getMessage());
				}
				transform.put("result", r);
			}
			return responseJSON(exchange, transform);
		}
	}

	CertServer(CAService ca, int port, OcspServer ocspServer, Element root, AuthCode authCode, Archive archive)
			throws Exception {
		this.ca = ca;
		this.caLifetime = Instant.ofEpochMilli(Arrays.stream(ca.getCACertificates()).map(X509Certificate::getNotAfter)
				.max(Comparator.comparingLong(Date::getTime)).get().getTime());
		this.port = port;
		this.ocspServer = ocspServer;
		this.authCode = authCode;
		this.archive = archive;
		ElementHelper eh = new ElementHelper(root);
		String[] algo = eh.getString("publicKeyAlgorithmForGeneratePKCS12", "rsa/1024").split("/");
		this.privateKeyGenerator = KeyPairGenerator.getInstance(algo[0].toUpperCase());
		this.privateKeyGenerator.initialize(Integer.parseInt(algo[1]));
		eh = XMLUtils.getChildElements(root).stream().filter(e -> e.getTagName().equals("Subject"))
				.map(ElementHelper::new).findAny().get();
		this.subjectPattern = Pattern.compile(eh.getString("pattern", ".*"),
				eh.getBoolean("patternIgnorecase", false) ? Pattern.CASE_INSENSITIVE : 0);
		this.subjectTemplate = eh.getString("template");
		eh = XMLUtils.getChildElements(root).stream().filter(e -> e.getTagName().equals("NotBefore"))
				.map(ElementHelper::new).findAny().get();
		this.notBeforeMandatory = eh.getBoolean("mandatory", false);
		eh = XMLUtils.getChildElements(root).stream().filter(e -> e.getTagName().equals("NotAfter"))
				.map(ElementHelper::new).findAny().get();
		this.notAfterMandatory = eh.getBoolean("mandatory", false);
		this.notAfterPeriod = TimeUnit.DAYS.toMillis(eh.getInt("period", 30));
		if (notAfterMandatory) {
			this.notAfterPeriodLow = this.notAfterPeriodHigh = this.notAfterPeriod;
		} else {
			this.notAfterPeriodLow = TimeUnit.DAYS.toMillis(eh.getInt("periodLow"));
			this.notAfterPeriodHigh = TimeUnit.DAYS.toMillis(eh.getInt("periodHigh"));
		}
		if (notAfterPeriodLow > notAfterPeriodHigh || notAfterPeriod < notAfterPeriodLow
				|| notAfterPeriod > notAfterPeriodHigh) {
			Trace.fatal("CertServer NotAfter config error, periodLow <= period <= periodHigh must be satisfied.");
			System.exit(-1);
		}
		this.keyUsage = XMLUtils
				.getChildElements(XMLUtils.getChildElements(root).stream()
						.filter(e -> e.getTagName().equals("KeyUsage")).findAny().get())
				.stream().collect(() -> new EnumMap<KeyUsage, Integer>(KeyUsage.class), (m, e) -> {
					int mask = 0;
					ElementHelper _eh = new ElementHelper(e);
					if (_eh.getBoolean("mandatory", false))
						mask |= MASK_USAGE_MANDATORY;
					if (_eh.getBoolean("default", false))
						mask |= MASK_USAGE_TRUE;
					m.put(KeyUsage.valueOf(e.getTagName()), mask);
				}, EnumMap::putAll);
		this.extKeyUsage = XMLUtils
				.getChildElements(XMLUtils.getChildElements(root).stream()
						.filter(e -> e.getTagName().equals("ExtKeyUsage")).findAny().get())
				.stream().collect(() -> new EnumMap<ExtKeyUsage, Integer>(ExtKeyUsage.class), (m, e) -> {
					int mask = 0;
					ElementHelper _eh = new ElementHelper(e);
					if (_eh.getBoolean("mandatory", false))
						mask |= MASK_USAGE_MANDATORY;
					if (_eh.getBoolean("default", false))
						mask |= MASK_USAGE_TRUE;
					m.put(ExtKeyUsage.valueOf(e.getTagName()), mask);
				}, EnumMap::putAll);
	}

	void start() throws Exception {
		InetSocketAddress addr = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
		HttpServer server = HttpServer.create(addr);
		server.createContext("/", new Handler());
		server.createContext("/sign", new HandlerSign());
		server.createContext("/revoke", new HandlerMaintance("revoke", c -> ocspServer.revoke(c)));
		server.createContext("/recall", new HandlerMaintance("recall", c -> ocspServer.recall(c)));
		server.createContext("/parse", new HttpHandler() {
			@Override
			public void censor(HttpExchange exchange) {
				exchange.getFormData().postLimit(CERTFILE_MAX_SIZE);
			}

			@Override
			public DataSupplier handle(HttpExchange exchange) throws Exception {
				try {
					Certificate[] chain = CertificateFactory.getInstance("X.509")
							.generateCertificates(new ByteArrayInputStream(exchange.getFormData().getRaw().getBytes()))
							.toArray(new Certificate[0]);
					Certificate cert = chain.length == 1 ? chain[0] : SecurityUtils.sortCertificateChain(chain)[0];
					return DataSupplier.from(SecurityUtils.encodePEM("CERTIFICATE", cert.getEncoded()),
							StandardCharsets.UTF_8);
				} catch (Exception e) {
				}
				throw new HttpException(HttpURLConnection.HTTP_BAD_REQUEST, true);
			}
		});
		Octets data = new Octets();
		try (InputStream in = CertServer.class.getResourceAsStream("CertServer.html")) {
			new StreamSource(in, new SinkOctets(data)).flush();
		}
		server.createContext("/CertServer.html", new StaticWebData(data.getBytes(), "text/html; charset=utf-8"));
		data.clear();
		try (InputStream in = CertServer.class.getResourceAsStream("CertServer.js")) {
			new StreamSource(in, new SinkOctets(data)).flush();
		}
		server.createContext("/CertServer.js", new StaticWebData(data.getBytes(), "text/javascript; charset=utf-8"));
		server.start();
		if (Trace.isInfoEnabled())
			Trace.info("CertServer start on " + addr);
	}
}
