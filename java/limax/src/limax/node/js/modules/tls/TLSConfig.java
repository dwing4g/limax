package limax.node.js.modules.tls;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CRL;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.script.Invocable;

import limax.codec.asn1.ASN1Object;
import limax.codec.asn1.ASN1ObjectIdentifier;
import limax.codec.asn1.ASN1Sequence;
import limax.codec.asn1.ASN1Set;
import limax.codec.asn1.ASN1String;
import limax.codec.asn1.DecodeBER;
import limax.node.js.Buffer;
import limax.util.SecurityUtils;
import limax.util.Trace;

public class TLSConfig {
	interface X509CertificateChainChecker {
		boolean accept(X509Certificate[] chain, CertificateException exception) throws Exception;
	}

	private final static Set<String> protocols = new HashSet<>(Arrays.asList("sslv3", "tlsv1", "tlsv1.1", "tlsv1.2"));
	private final static Set<TrustAnchor> defaultTrustAnchors;
	private final Invocable invocable;
	private final List<PrivateKeyEntry> keyEntries = new ArrayList<>();
	private final Set<TrustAnchor> trustAnchors = new HashSet<>();
	private final Set<CRL> crls = new HashSet<>();
	private final Map<Integer, SNIServerName> serverNames = new LinkedHashMap<>();
	private int serial;
	private boolean needClientAuth = false;
	private String protocol = "tlsv1.2";
	private X509CertificateChainChecker trustChecker;
	private boolean positiveCheck = false;
	private boolean revocationEnabled = false;
	static {
		Set<TrustAnchor> _defaultTrustAnchors = null;
		try {
			KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			keyStore.load(new FileInputStream(
					Paths.get(System.getProperty("java.home"), "lib", "security", "cacerts").toFile()), null);
			_defaultTrustAnchors = new PKIXParameters(keyStore).getTrustAnchors();
		} catch (Exception e) {
			if (Trace.isFatalEnabled())
				Trace.fatal("load system cacerts", e);
			System.exit(-1);
		}
		defaultTrustAnchors = _defaultTrustAnchors;
	}

	public TLSConfig(Invocable invocable) {
		this.invocable = invocable;
	}

	private static KeyStore.PrivateKeyEntry createPrivateKeyEntry(PrivateKey privateKey,
			Collection<? extends Certificate> certs) throws Exception {
		Set<X509Certificate> ca = certs.stream().map(c -> (X509Certificate) c)
				.filter(c -> c.getSubjectX500Principal().equals(c.getIssuerX500Principal()))
				.collect(Collectors.toSet());
		if (ca.isEmpty())
			throw new CertificateException("CertificateChain Contains nonCA");
		X509Certificate cert = (X509Certificate) certs.iterator().next();
		if (ca.contains(cert))
			return new KeyStore.PrivateKeyEntry(privateKey, new Certificate[] { cert });
		X509CertSelector selector = new X509CertSelector();
		selector.setCertificate(cert);
		CertStore store = CertStore.getInstance("Collection", new CollectionCertStoreParameters(certs));
		PKIXBuilderParameters pbp = new PKIXBuilderParameters(
				ca.stream().map(ce -> new TrustAnchor(ce, null)).collect(Collectors.toSet()), selector);
		pbp.addCertStore(store);
		pbp.setRevocationEnabled(false);
		List<? extends Certificate> list = CertPathBuilder.getInstance("PKIX").build(pbp).getCertPath()
				.getCertificates();
		selector = new X509CertSelector();
		selector.setSubject(((X509Certificate) list.get(list.size() - 1)).getIssuerX500Principal());
		return new KeyStore.PrivateKeyEntry(privateKey,
				Stream.concat(list.stream(), store.getCertificates(selector).stream()).toArray(Certificate[]::new));
	}

	private static PrivateKey loadPrivateKey(String algorithm, KeySpec keySpec) {
		try {
			return KeyFactory.getInstance(algorithm).generatePrivate(keySpec);
		} catch (Exception e) {
			return null;
		}
	}

	private TrustManager[] createTrustManagers() throws Exception {
		PKIXBuilderParameters pkixParams = new PKIXBuilderParameters(trustAnchors, new X509CertSelector());
		pkixParams.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(crls)));
		pkixParams.setRevocationEnabled(revocationEnabled);
		TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
		trustManagerFactory.init(new CertPathTrustManagerParameters(pkixParams));
		return trustManagerFactory.getTrustManagers();
	}

	private KeyManager[] createKeyManagers() throws Exception {
		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, null);
		for (PrivateKeyEntry entry : keyEntries)
			keyStore.setEntry(Integer.toString(serial++), entry, new KeyStore.PasswordProtection(new char[] {}));
		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("PKIX");
		keyManagerFactory.init(keyStore, new char[] {});
		return keyManagerFactory.getKeyManagers();
	}

	private final static ASN1Object OID_CN = new ASN1ObjectIdentifier("2.5.4.3");

	private static String getSubjectCNString(X509Certificate cer) {
		try {
			return ((ASN1String) ((ASN1Sequence) DecodeBER.decode(cer.getSubjectX500Principal().getEncoded()))
					.getChildren().stream().map(item -> (ASN1Sequence) ((ASN1Set) item).get(0))
					.filter(item -> item.get(0).equals(OID_CN)).findFirst().get().get(1)).get();
		} catch (Exception e) {
		}
		return null;
	}

	private static List<String> getSubjectAlternativeDNSNames(X509Certificate cer) {
		try {
			return cer.getSubjectAlternativeNames().stream().filter(item -> (Integer) item.get(0) == 2)
					.map(item -> (String) item.get(1)).collect(Collectors.toList());
		} catch (Exception e) {
			return new ArrayList<String>();
		}
	}

	static Pattern getDNSPattern(X509Certificate cer) {
		List<String> domains = getSubjectAlternativeDNSNames(cer);
		String cn = getSubjectCNString(cer);
		if (cn != null)
			domains.add(cn);
		return Pattern.compile(domains.stream().map(s -> s.replace(".", "\\.").replace("*", "[^.&&\\S]+"))
				.collect(Collectors.joining("|")));
	}

	public void addPKCS12(Object data, Object pass) throws Exception {
		KeyStore pkcs12 = KeyStore.getInstance("PKCS12");
		char[] password = (pass instanceof String ? ((String) pass) : ((Buffer) pass).toString()).toCharArray();
		try (InputStream istream = data instanceof String
				? Files.exists(Paths.get((String) data)) ? new FileInputStream((String) data)
						: new ByteArrayInputStream(((String) data).getBytes())
				: Files.exists(Paths.get(((Buffer) data).toString())) ? new FileInputStream(((Buffer) data).toString())
						: new ByteArrayInputStream(((Buffer) data).toByteArray())) {
			pkcs12.load(istream, password);
			for (Enumeration<String> e = pkcs12.aliases(); e.hasMoreElements();)
				keyEntries.add(
						(PrivateKeyEntry) pkcs12.getEntry(e.nextElement(), new KeyStore.PasswordProtection(password)));
		}
	}

	public void addPrivateKeyAndCertificatePack(Object _pkey, Object _cert, Object pass) throws Exception {
		String pkey = _pkey instanceof String ? (String) _pkey : ((Buffer) _pkey).toString();
		String pem = Files.exists(Paths.get(pkey)) ? new String(Files.readAllBytes(Paths.get(pkey))) : pkey;
		KeySpec keySpec = (KeySpec) SecurityUtils.loadPEM(pem,
				(pass instanceof String ? (String) pass : ((Buffer) pass).toString()).toCharArray());
		PrivateKey privateKey = loadPrivateKey("RSA", keySpec);
		if (privateKey == null)
			privateKey = loadPrivateKey("DSA", keySpec);
		if (privateKey == null)
			privateKey = loadPrivateKey("EC", keySpec);
		if (privateKey == null)
			throw new NoSuchAlgorithmException();
		String cert = _cert instanceof String ? (String) _cert : ((Buffer) _cert).toString();
		try (InputStream istream = Files.exists(Paths.get(cert)) ? new FileInputStream(cert)
				: new ByteArrayInputStream(cert.getBytes())) {
			keyEntries.add(createPrivateKeyEntry(privateKey,
					CertificateFactory.getInstance("X.509").generateCertificates(istream)));
		}
	}

	public void addTrustCertificate(Object data) throws Exception {
		try (InputStream istream = data instanceof String
				? Files.exists(Paths.get((String) data)) ? new FileInputStream((String) data)
						: new ByteArrayInputStream(((String) data).getBytes())
				: Files.exists(Paths.get(((Buffer) data).toString())) ? new FileInputStream(((Buffer) data).toString())
						: new ByteArrayInputStream(((Buffer) data).toByteArray())) {
			CertificateFactory.getInstance("X.509").generateCertificates(istream).stream()
					.map(cert -> new TrustAnchor((X509Certificate) cert, null))
					.collect(() -> trustAnchors, Set::add, Set::addAll);
		}
	}

	public void addCRL(Object data) throws Exception {
		try (InputStream istream = data instanceof String
				? Files.exists(Paths.get((String) data)) ? new FileInputStream((String) data)
						: new ByteArrayInputStream(((String) data).getBytes())
				: Files.exists(Paths.get(((Buffer) data).toString())) ? new FileInputStream(((Buffer) data).toString())
						: new ByteArrayInputStream(((Buffer) data).toByteArray())) {
			crls.addAll(CertificateFactory.getInstance("X.509").generateCRLs(istream));
		}
	}

	public void setRevocationEnabled(boolean revocationEnabled) {
		this.revocationEnabled = revocationEnabled;
	}

	public void addAllCA() {
		trustAnchors.addAll(defaultTrustAnchors);
	}

	public void setProtocol(String protocol) {
		String _protocol = protocol.toLowerCase();
		if (protocols.contains(_protocol))
			this.protocol = _protocol;
	}

	private void addSNIServerName(SNIServerName serverName) {
		if (serverNames.put(serverName.getType(), serverName) != null)
			throw new IllegalArgumentException("Duplicated server name of type " + serverName.getType());
	}

	public void setSNIHostName(String hostname) {
		addSNIServerName(new SNIHostName(hostname));
	}

	public void setSNIHostName(Buffer hostname) {
		setSNIHostName(hostname.toString());
	}

	public void addSNIServerName(Number type, Buffer name) {
		addSNIServerName(new SNIServerName(type.intValue(), name.toByteArray()) {
		});
	}

	public void setTrustChecker(Object trustChecker) {
		this.trustChecker = (chain,
				exception) -> (Boolean) invocable.invokeMethod(trustChecker, "call", null, chain, exception);
		this.positiveCheck = false;
	}

	public void setPositiveTrustChecker(Object trustChecker) {
		setTrustChecker(trustChecker);
		this.positiveCheck = true;
	}

	public void setNeedClientAuth(boolean needClientAuth) {
		this.needClientAuth = needClientAuth;
	}

	public boolean getNeedClientAuth() {
		return needClientAuth;
	}

	public List<SNIServerName> getServerNames() {
		return new ArrayList<SNIServerName>(serverNames.values());
	}

	public String getProtocol() {
		return protocol;
	}

	public KeyManager[] getKeyManagers() throws Exception {
		return TLSKeyManager.create(createKeyManagers(),
				keyEntries.size() > 1 ? new VirtualServerKeyManager(keyEntries) : null);
	}

	public TrustManager[] getTrustManagers() throws Exception {
		return TLSTrustManager.create(createTrustManagers(), trustChecker, positiveCheck);
	}
}