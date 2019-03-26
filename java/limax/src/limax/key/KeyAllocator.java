package limax.key;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;

import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.codec.SinkOctets;
import limax.codec.StreamSource;
import limax.codec.asn1.ASN1ObjectIdentifier;
import limax.codec.asn1.ASN1OctetString;
import limax.codec.asn1.ASN1PrimitiveObject;
import limax.codec.asn1.ASN1Sequence;
import limax.codec.asn1.ASN1Set;
import limax.codec.asn1.ASN1String;
import limax.codec.asn1.ASN1Tag;
import limax.codec.asn1.DecodeBER;
import limax.codec.asn1.TagClass;
import limax.net.Engine;
import limax.pkix.SSLContextAllocator;
import limax.util.Pair;
import limax.util.XMLUtils;

public class KeyAllocator {
	private static final long DEFAULT_PRECISION = Long.getLong("limax.key.KeyAllocator.DEFAULT_PRECISION", 3600000L);
	private static final int TIMEOUT = Integer.getInteger("limax.key.KeyAllocator.TIMEOUT", 3000);
	private static final int ISOLATED_SERVER_THRESHOLD = Integer
			.getInteger("limax.key.KeyAllocator.ISOLATED_SERVER_THRESHOLD", 0);
	private static final int DEFAULT_CACHE_CAPACITY = Integer
			.getInteger("limax.key.KeyAllocator.DEFAULT_CACHE_CAPACITY", 1024);
	private static final ASN1Tag tagDNS = new ASN1Tag(TagClass.ContextSpecific, 2);
	private static final ASN1Tag tagURI = new ASN1Tag(TagClass.ContextSpecific, 6);
	private static final ASN1ObjectIdentifier OID_CN = new ASN1ObjectIdentifier("2.5.4.3");
	private final SSLContextAllocator sslContextAllocator;
	private final ServerEvaluate serverEvaluate;
	private final String dNSName;
	private volatile String httpsHost;
	private final HostnameVerifier hostnameVerifier;
	private final Map<URI, Pair<URI, Long>> uris = new HashMap<>();
	private final Map<KeyIdent, byte[]> cache;
	private long timestamp;

	public KeyAllocator(SSLContextAllocator sslContextAllocator, int cacheCapacity) throws KeyException, Exception {
		this.sslContextAllocator = sslContextAllocator;
		ASN1Sequence seq = (ASN1Sequence) DecodeBER.decode(((ASN1OctetString) DecodeBER
				.decode(((X509Certificate) sslContextAllocator.getKeyInfo().getCertificateChain()[0])
						.getExtensionValue("2.5.29.17"))).get());
		String dNSName = null;
		for (int i = 0; i < seq.size(); i++) {
			ASN1PrimitiveObject item = (ASN1PrimitiveObject) seq.get(i);
			if (item.getTag().equals(tagURI)) {
				URI uri0 = URI.create(new String(item.getData(), StandardCharsets.ISO_8859_1));
				URI uri1 = new URI(uri0.getScheme(), uri0.getSchemeSpecificPart(), null);
				uris.put(uri1, new Pair<>(uri1, getPrecision(uri0.getFragment())));
			} else if (item.getTag().equals(tagDNS))
				dNSName = new String(item.getData(), StandardCharsets.ISO_8859_1);
		}
		if (dNSName == null)
			throw new KeyException(KeyException.Type.SubjectAltNameWithoutDNSName);
		this.dNSName = dNSName;
		this.httpsHost = dNSName;
		this.serverEvaluate = new ServerEvaluate(dNSName);
		if (this.uris.isEmpty())
			throw new KeyException(KeyException.Type.SubjectAltNameWithoutURI);
		this.cache = new LinkedHashMap<KeyIdent, byte[]>() {
			private static final long serialVersionUID = 1591025484627828983L;

			@Override
			protected boolean removeEldestEntry(Map.Entry<KeyIdent, byte[]> eldest) {
				return size() > cacheCapacity;
			}
		};
		this.hostnameVerifier = (hostname, session) -> {
			try {
				return getSubjectCN(((X509Certificate) session.getPeerCertificates()[0]))
						.equalsIgnoreCase(this.dNSName);
			} catch (Exception e) {
				return false;
			}
		};
	}

	public KeyAllocator(SSLContextAllocator sslContextAllocator) throws KeyException, Exception {
		this(sslContextAllocator, DEFAULT_CACHE_CAPACITY);
	}

	private static long getPrecision(String fragment) {
		try {
			int len = fragment.length();
			switch (fragment.charAt(len - 1)) {
			case 'h':
			case 'H':
				return TimeUnit.HOURS.toMillis(Long.parseLong(fragment.substring(0, len - 1)));
			case 'm':
			case 'M':
				return TimeUnit.MINUTES.toMillis(Long.parseLong(fragment.substring(0, len - 1)));
			case 's':
			case 'S':
				return TimeUnit.SECONDS.toMillis(Long.parseLong(fragment.substring(0, len - 1)));
			}
			return Long.parseLong(fragment);
		} catch (Exception e) {
		}
		return DEFAULT_PRECISION;
	}

	private byte[] alloc(KeyIdent keyIdent) throws Exception {
		synchronized (this) {
			long timestamp = keyIdent.getTimestamp();
			if (timestamp == -1)
				timestamp = this.timestamp;
			byte[] key = cache.get(new KeyIdent(timestamp, keyIdent.getNonce(), keyIdent.getGroup()));
			if (key != null) {
				keyIdent.setTimestamp(timestamp);
				return key;
			}
		}
		Exception exception = null;
		for (InetAddress inetAddress : serverEvaluate.servers()) {
			try {
				HttpsURLConnection connection = (HttpsURLConnection) new URL("https", inetAddress.getHostAddress(), "/")
						.openConnection();
				connection.setConnectTimeout(TIMEOUT);
				connection.setReadTimeout(TIMEOUT);
				connection.setHostnameVerifier(hostnameVerifier);
				connection.setSSLSocketFactory(sslContextAllocator.alloc().getSocketFactory());
				connection.setRequestProperty("Content-Type", "application/octet-stream");
				connection.setDoOutput(true);
				connection.connect();
				try (OutputStream out = connection.getOutputStream()) {
					OctetsStream os = new OctetsStream().marshal(keyIdent);
					out.write(os.array(), 0, os.size());
				}
				try (InputStream in = connection.getInputStream()) {
					OctetsStream os = new OctetsStream();
					new StreamSource(in, new SinkOctets(os)).flush();
					KeyResponse response = new KeyResponse(os);
					Collection<InetAddress> randomServers = response.getRandomServers();
					if (randomServers.size() < ISOLATED_SERVER_THRESHOLD)
						throw new Exception("return " + randomServers.size()
								+ " servers below ISOLATED_SERVER_THRESHHOLD = " + ISOLATED_SERVER_THRESHOLD);
					serverEvaluate.addDynamicServer(randomServers, TIMEOUT);
					long timestamp = response.getTimestamp();
					byte[] key = response.getKey();
					keyIdent.setTimestamp(timestamp);
					synchronized (this) {
						cache.put(keyIdent, key);
						if (this.timestamp < timestamp)
							this.timestamp = timestamp;
					}
					return key;
				}
			} catch (Exception e) {
				serverEvaluate.drop(inetAddress);
				exception = e;
			}
		}
		throw exception;
	}

	public KeyDesc createKeyDesc(URI uri) throws KeyException, Exception {
		if (uri.getFragment() != null)
			uri = new URI(uri.getScheme(), uri.getSchemeSpecificPart(), null);
		Pair<URI, Long> current = uris.get(uri.normalize());
		if (current == null)
			throw new KeyException(KeyException.Type.UnsupportedURI, uri.toString());
		KeyIdent keyIdent = new KeyIdent(-1L, System.currentTimeMillis() / current.getValue(),
				current.getKey().toString());
		byte[] key = alloc(keyIdent);
		return new KeyDesc(new OctetsStream().marshal(keyIdent).getBytes(), key);
	}

	public KeyDesc createKeyDesc(byte[] ident) throws KeyException, Exception {
		KeyIdent keyIdent;
		try {
			keyIdent = new KeyIdent(OctetsStream.wrap(Octets.wrap(ident)));
		} catch (Exception e) {
			throw new KeyException(KeyException.Type.MalformedKeyIdent);
		}
		if (!uris.containsKey(URI.create(keyIdent.getGroup())))
			throw new KeyException(KeyException.Type.UnsupportedURI, keyIdent.getGroup());
		long timestamp = keyIdent.getTimestamp();
		byte[] key = alloc(keyIdent);
		if (keyIdent.getTimestamp() != timestamp)
			throw new KeyException(KeyException.Type.ServerRekeyed);
		return new KeyDesc(ident, key);
	}

	public Map<URI, Long> getURIs() {
		return uris.values().stream().collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
	}

	public String getDNSName() {
		return dNSName;
	}

	public void setHost(String httpsHost) throws UnknownHostException {
		serverEvaluate.addStaticServer(this.httpsHost = httpsHost);
	}

	public String getHost() {
		return this.httpsHost;
	}

	static boolean containsURI(X509Certificate cer, URI req) {
		try {
			ASN1Sequence seq = (ASN1Sequence) DecodeBER
					.decode(((ASN1OctetString) DecodeBER.decode(cer.getExtensionValue("2.5.29.17"))).get());
			for (int i = 0; i < seq.size(); i++) {
				ASN1PrimitiveObject item = (ASN1PrimitiveObject) seq.get(i);
				if (item.getTag().equals(tagURI)) {
					URI uri = URI.create(new String(item.getData(), StandardCharsets.ISO_8859_1));
					if (req.equals(new URI(uri.getScheme(), uri.getSchemeSpecificPart(), null)))
						return true;
				}
			}
		} catch (Exception e) {
		}
		return false;
	}

	static String getSubjectCN(X509Certificate cert) {
		try {
			ASN1Sequence dn = (ASN1Sequence) DecodeBER.decode(cert.getSubjectX500Principal().getEncoded());
			for (int i = 0; i < dn.size(); i++) {
				ASN1Set rdn = (ASN1Set) dn.get(i);
				for (int j = 0; j < rdn.size(); j++) {
					ASN1Sequence item = (ASN1Sequence) rdn.get(j);
					if (item.get(0).equals(OID_CN))
						return ((ASN1String) item.get(1)).get();
				}
			}
		} catch (Exception e) {
		}
		return null;
	}

	static void main(String[] args) throws Exception {
		Path path = Paths.get(args.length == 0 ? "keyserver.xml" : args[0]);
		if (!Files.isReadable(path)) {
			System.out.println("Usage: java -jar limax.jar keyserver [path to keyserver.xml]");
			return;
		}
		byte[] data = Files.readAllBytes(path);
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		XMLUtils.prettySave(XMLUtils.getRootElement(new ByteArrayInputStream(data)).getOwnerDocument(), os);
		if (!Arrays.equals(os.toByteArray(), data))
			Files.write(path, os.toByteArray());
		Engine.open(4, 16, 64);
		new KeyServer(path.toAbsolutePath().getParent(), XMLUtils.getRootElement(new ByteArrayInputStream(data)))
				.start();
	}
}
