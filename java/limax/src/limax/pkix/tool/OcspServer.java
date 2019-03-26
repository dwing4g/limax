package limax.pkix.tool;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.security.auth.x500.X500Principal;

import limax.codec.asn1.ASN1BitString;
import limax.codec.asn1.ASN1Sequence;
import limax.codec.asn1.DecodeBER;
import limax.http.HttpServer;
import limax.util.Trace;

class OcspServer {
	private final Map<OcspIssuerKey, X509Certificate> issuerKeys = new HashMap<>();
	private final Map<X509Certificate, Map<BigInteger, Long>> revokeMap = new HashMap<>();
	private final OcspResponseCache cache;
	private final int port;
	private final URI ocspURI;
	private final URI baseCRLDP;
	private final OcspFileStore ocspFileStore;
	private final OcspSignerConfig ocspSignerConfig;

	OcspServer(OcspSignerConfig ocspSignerConfig, int port, String domain, Path ocspStore, int responseCacheCapacity)
			throws Exception {
		this.ocspSignerConfig = ocspSignerConfig;
		ScheduledExecutorService scheduler = ocspSignerConfig.getScheduler();
		this.port = port;
		URI baseURI = new URI("http", domain, "/", null);
		this.ocspURI = baseURI.resolve("ocsp");
		this.baseCRLDP = baseURI.resolve("crl/");
		this.ocspFileStore = new OcspFileStore(ocspStore, this, scheduler);
		this.cache = new OcspResponseCache(responseCacheCapacity);
		for (X509Certificate cacert : ocspSignerConfig.getCACertificates()) {
			ASN1Sequence subjectPublicKeyInfo = ((ASN1Sequence) DecodeBER.decode(cacert.getPublicKey().getEncoded()));
			ASN1BitString subjectPublicKey = (ASN1BitString) subjectPublicKeyInfo.get(1);
			byte[] issuerName = cacert.getSubjectX500Principal().getEncoded();
			byte[] issuerKey = subjectPublicKey.get().toByteArray();
			for (String algorithm : Arrays.asList("1.3.14.3.2.26", "2.16.840.1.101.3.4.2.1", "2.16.840.1.101.3.4.2.2",
					"2.16.840.1.101.3.4.2.3", "2.16.840.1.101.3.4.2.4")) {
				MessageDigest md = MessageDigest.getInstance(algorithm);
				this.issuerKeys.put(new OcspIssuerKey(algorithm, md.digest(issuerName), md.digest(issuerKey)), cacert);
			}
			revokeMap.put(cacert, ocspFileStore.addCA(cacert));
			scheduler.schedule(() -> dropCA(cacert), cacert.getNotAfter().getTime() - System.currentTimeMillis(),
					TimeUnit.MILLISECONDS);
		}
	}

	private void dropCA(X509Certificate cacert) {
		if (Trace.isInfoEnabled())
			Trace.info("OcspServer drop CA [" + cacert.getSubjectX500Principal() + "]");
		ocspFileStore.dropCA(cacert);
		revokeMap.remove(cacert).keySet().forEach(serial -> cache.invalidate(serial));
	}

	private OcspCertStatus query(OcspCertID certID) {
		X509Certificate cacert = issuerKeys.get(certID.getIssuerKey());
		if (cacert == null)
			return null;
		Long revocationTime = revokeMap.get(cacert).get(certID.getSerialNumber());
		return new OcspCertStatus(certID.getCertID(), certID.getSerialNumber(), revocationTime);
	}

	URI getOcspURI() {
		return ocspURI;
	}

	URI getCRLDP(X509Certificate cacert) {
		return baseCRLDP.resolve(cacert.getSerialNumber().toString(16) + ".crl");
	}

	private X509Certificate verify(X509Certificate cert) {
		X500Principal issuer = cert.getIssuerX500Principal();
		for (X509Certificate cacert : ocspSignerConfig.getCACertificates()) {
			try {
				if (issuer.equals(cacert.getSubjectX500Principal())) {
					cert.verify(cacert.getPublicKey());
					return cacert;
				}
			} catch (Exception e) {
			}
		}
		return null;
	}

	private X509Certificate verify(X509Certificate cert, long now) throws CertificateException {
		if (cert.getNotAfter().before(new Date(now)))
			throw new CertificateExpiredException("Expired Certificate.");
		X509Certificate cacert = verify(cert);
		if (cacert == null)
			throw new CertificateException("Certificate is not signed by the CA.");
		return cacert;
	}

	public void revoke(X509Certificate cert) throws Exception {
		long now = System.currentTimeMillis();
		X509Certificate cacert = verify(cert, now);
		if (cert.equals(cacert))
			throw new CertificateException("Revoke CA self");
		Map<BigInteger, Long> revokes = revokeMap.get(cacert);
		BigInteger serialNumber = cert.getSerialNumber();
		if (revokes.containsKey(serialNumber))
			throw new Exception("Repetitive revoke");
		ocspFileStore.addRevoked(cacert, cert, now);
		revokes.put(serialNumber, now);
		cache.invalidate(serialNumber);
	}

	void recall(X509Certificate cert) throws Exception {
		X509Certificate cacert = verify(cert, System.currentTimeMillis());
		Map<BigInteger, Long> revokes = revokeMap.get(cacert);
		BigInteger serialNumber = cert.getSerialNumber();
		if (!revokes.containsKey(serialNumber))
			throw new Exception("Certificate is not revoked");
		ocspFileStore.removeRevoked(cacert, cert);
	}

	public void recall(BigInteger serialNumber) {
		revokeMap.values().forEach(revokes -> revokes.remove(serialNumber));
		cache.invalidate(serialNumber);
	}

	PKIXRevocationChecker getCertPathChecker() {
		return new PKIXRevocationChecker() {
			@Override
			public void init(boolean forward) throws CertPathValidatorException {
			}

			@Override
			public boolean isForwardCheckingSupported() {
				return false;
			}

			@Override
			public void check(Certificate cert) throws CertPathValidatorException {
				if (verify((X509Certificate) cert) == null)
					return;
				BigInteger serialNumber = ((X509Certificate) cert).getSerialNumber();
				for (Map<BigInteger, Long> revokes : revokeMap.values())
					if (revokes.containsKey(serialNumber))
						throw new CertPathValidatorException();
			}

			@Override
			public Set<String> getSupportedExtensions() {
				return null;
			}

			@Override
			public void check(Certificate cert, Collection<String> unresolvedCritExts)
					throws CertPathValidatorException {
				check(cert);
			}

			@Override
			public List<CertPathValidatorException> getSoftFailExceptions() {
				return Collections.emptyList();
			}
		};
	}

	void start() throws Exception {
		InetSocketAddress addr = new InetSocketAddress(port);
		HttpServer server = HttpServer.create(addr);
		server.createContext(ocspURI.getPath(), new OcspHttpHandler(certID -> query(certID), cache, ocspSignerConfig));
		server.createContext(baseCRLDP.getPath(),
				new CRLHttpHandler(ocspSignerConfig,
						() -> revokeMap.entrySet().stream().collect(Collectors.toMap(e -> getCRLDP(e.getKey()),
								e -> ocspSignerConfig.getCRLSigner().apply(e.getKey(), e.getValue())))));
		server.start();
		if (Trace.isInfoEnabled())
			Trace.info("OcspServer start on " + addr);
		ocspFileStore.start();
	}
}
