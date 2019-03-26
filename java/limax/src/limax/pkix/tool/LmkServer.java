package limax.pkix.tool;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.security.auth.x500.X500Principal;

import limax.codec.Octets;
import limax.codec.asn1.ASN1OctetString;
import limax.codec.asn1.ASN1PrimitiveObject;
import limax.codec.asn1.ASN1Sequence;
import limax.codec.asn1.ASN1Tag;
import limax.codec.asn1.DecodeBER;
import limax.codec.asn1.TagClass;
import limax.http.DataSupplier;
import limax.http.HttpException;
import limax.http.HttpExchange;
import limax.http.HttpHandler;
import limax.pkix.CAService;
import limax.pkix.ExtKeyUsage;
import limax.pkix.GeneralName;
import limax.pkix.KeyUsage;
import limax.pkix.LmkBundleUtils;
import limax.pkix.TrustManager;
import limax.pkix.X509EndEntityCertificateParameter;
import limax.util.ConcurrentEnvironment;
import limax.util.ElementHelper;
import limax.util.SecurityUtils;

class LmkServer {
	private static final ScheduledThreadPoolExecutor scheduler = ConcurrentEnvironment.getInstance()
			.newScheduledThreadPool("LmkServer Scheduler", 5, true);
	private static final ASN1Tag tagDNS = new ASN1Tag(TagClass.ContextSpecific, 2);
	private final String domain;
	private final CAService ca;
	private final String lmkDomain;
	private final OcspServer ocspServer;
	private final AutoHttpsServer lmkServer;
	private final String certificateAlgorithm;
	private final int lifetime;
	private final int constraintNameLength;

	private class Handler implements HttpHandler {
		@Override
		public DataSupplier handle(HttpExchange exchange) {
			try {
				X509Certificate cert = (X509Certificate) exchange.getSSLSession().getPeerCertificates()[0];
				LmkResponse lmkResponse = LmkResponse.buildResponse(cert, exchange.getRequestURI().getQuery(),
						() -> lmkDomain != null ? lmkDomain : parseLmkDomain(cert), constraintNameLength,
						() -> Arrays.stream(ca.getCACertificates())
								.anyMatch(cacert -> SecurityUtils.isSignedBy(cert, cacert)));
				if (lmkResponse != null) {
					KeyPair keyPair = Main.keyPairGenerator(ca, certificateAlgorithm);
					X509Certificate[] chain = ca.sign(new X509EndEntityCertificateParameter() {
						@Override
						public X500Principal getSubject() {
							return lmkResponse.getSubject();
						}

						@Override
						public PublicKey getPublicKey() {
							return keyPair.getPublic();
						}

						@Override
						public Collection<GeneralName> getSubjectAltNames() {
							return lmkResponse.getSubjectAltNames(cert.getSubjectX500Principal());
						}

						@Override
						public Date getNotAfter() {
							return new Date(getNotBefore().getTime() + TimeUnit.DAYS.toMillis(lifetime));
						}

						@Override
						public EnumSet<KeyUsage> getKeyUsages() {
							return EnumSet.of(KeyUsage.digitalSignature);
						}

						@Override
						public EnumSet<ExtKeyUsage> getExtKeyUsages() {
							return EnumSet.of(ExtKeyUsage.ClientAuth, ExtKeyUsage.EmailProtection);
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
					Octets response = LmkBundleUtils
							.createInstance(keyPair.getPrivate(), Arrays.copyOf(chain, chain.length - 1))
							.save(lmkResponse.getPassphrase());
					exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
					exchange.getResponseHeaders().set("Content-Disposition",
							"attachment; filename=\"" + lmkResponse.toFileNameString());
					return DataSupplier.from(response.getByteBuffer());
				}
			} catch (Exception e) {
			}
			throw new HttpException(HttpURLConnection.HTTP_BAD_REQUEST, true);
		}
	}

	private static String parseLmkDomain(X509Certificate cert) {
		try {
			ASN1Sequence seq = (ASN1Sequence) DecodeBER
					.decode(((ASN1OctetString) DecodeBER.decode(cert.getExtensionValue("2.5.29.17"))).get());
			for (int i = 0; i < seq.size(); i++) {
				ASN1PrimitiveObject item = (ASN1PrimitiveObject) seq.get(i);
				if (item.getTag().equals(tagDNS))
					return new String(item.getData(), StandardCharsets.ISO_8859_1).toLowerCase();
			}
		} catch (Exception e) {
		}
		return null;
	}

	LmkServer(ServerConfig serverConfig) throws Exception {
		this.domain = serverConfig.getDomain();
		this.ca = serverConfig.getCAService();
		this.lmkDomain = parseLmkDomain(ca.getCACertificate());
		this.ocspServer = serverConfig.createOcspServer(ca, domain, scheduler);
		ElementHelper eh = new ElementHelper(serverConfig.getRoot());
		TrustManager trustManager = new TrustManager();
		trustManager.addTrust(Paths.get(eh.getString("trustsPath")));
		trustManager.setRevocationCheckerOptions(eh.getString("revocationCheckerOptions"));
		this.lmkServer = new AutoHttpsServer(ca, domain, eh.getInt("port", 443), eh.getString("certificateAlgorithm"),
				eh.getInt("certificateLifetime"), trustManager, new X509CertSelector(), ocspServer, scheduler);
		eh = new ElementHelper(serverConfig.getElement("LmkBundle"));
		this.certificateAlgorithm = "rsa/" + eh.getString("rsaBits");
		Main.keyPairGenerator(ca, certificateAlgorithm);
		this.lifetime = eh.getInt("certificateLifetime");
		this.constraintNameLength = eh.getInt("constraintNameLength", 16);
	}

	void start() throws Exception {
		ocspServer.start();
		lmkServer.start(Collections.singletonMap("/", new Handler()));
	}
}
