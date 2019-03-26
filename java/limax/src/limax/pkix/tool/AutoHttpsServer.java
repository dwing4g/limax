package limax.pkix.tool;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;

import limax.http.HttpHandler;
import limax.http.HttpServer;
import limax.pkix.CAService;
import limax.pkix.ExtKeyUsage;
import limax.pkix.GeneralName;
import limax.pkix.KeyUsage;
import limax.pkix.TrustManager;
import limax.pkix.X509EndEntityCertificateParameter;
import limax.util.Trace;

class AutoHttpsServer {
	private static final int RESTART_DELAY_SECOND = 2;
	private static final long RESTART_MILLISECOND_BEFORE_CERTIFICATE_EXPIRE = 10000L;
	private static final char[] passphrase = "passphrase".toCharArray();
	private final CAService ca;
	private final String domain;
	private final int port;
	private final String certificateAlgorithm;
	private final int lifetime;
	private final TrustManager trustManager;
	private final X509CertSelector selector;
	private final OcspServer ocspServer;
	private final ScheduledExecutorService scheduler;

	AutoHttpsServer(CAService ca, String domain, int port, String certificateAlgorithm, int lifetime,
			TrustManager trustManager, X509CertSelector selector, OcspServer ocspServer,
			ScheduledExecutorService scheduler) {
		this.ca = ca;
		this.domain = domain;
		this.port = port;
		this.certificateAlgorithm = certificateAlgorithm;
		this.trustManager = trustManager;
		this.lifetime = lifetime;
		this.selector = selector;
		this.ocspServer = ocspServer;
		this.scheduler = scheduler;
	}

	void start(Map<String, HttpHandler> handlers) throws Exception {
		KeyPair keyPair = Main.keyPairGenerator(ca, certificateAlgorithm);
		X509Certificate[] chain = ca.sign(new X509EndEntityCertificateParameter() {
			@Override
			public X500Principal getSubject() {
				return new X500Principal("cn=" + domain);
			}

			@Override
			public PublicKey getPublicKey() {
				return keyPair.getPublic();
			}

			@Override
			public EnumSet<KeyUsage> getKeyUsages() {
				return EnumSet.of(KeyUsage.digitalSignature, KeyUsage.keyEncipherment);
			}

			@Override
			public EnumSet<ExtKeyUsage> getExtKeyUsages() {
				return EnumSet.of(ExtKeyUsage.ServerAuth);
			}

			@Override
			public URI getOcspURI() {
				return ocspServer.getOcspURI();
			}

			@Override
			public Function<X509Certificate, URI> getCRLDPMapping() {
				return cacert -> ocspServer.getCRLDP(cacert);
			}

			@Override
			public Date getNotAfter() {
				return new Date(getNotBefore().getTime() + TimeUnit.DAYS.toMillis(lifetime));
			}

			@Override
			public Collection<GeneralName> getSubjectAltNames() {
				return Collections.singleton(GeneralName.createDNSName(domain));
			}
		});
		X509Certificate[] cas = ca.getCACertificates();
		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, null);
		keyStore.setKeyEntry("", keyPair.getPrivate(), passphrase, Arrays.copyOf(chain, chain.length - 1));
		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("PKIX");
		keyManagerFactory.init(keyStore, passphrase);
		Set<TrustAnchor> trustAnchors = Stream.concat(Arrays.stream(cas), Stream.of(chain[chain.length - 1]))
				.map(cert -> new TrustAnchor(cert, null)).collect(Collectors.toSet());
		PKIXBuilderParameters pkixBuilderParameters;
		if (trustManager == null)
			pkixBuilderParameters = new PKIXBuilderParameters(trustAnchors, selector);
		else {
			TrustManager trustManager = (TrustManager) this.trustManager.clone();
			trustAnchors.forEach(anchor -> trustManager.addTrust(anchor));
			pkixBuilderParameters = trustManager.createPKIXBuilderParameters(selector, true);
		}
		pkixBuilderParameters.addCertPathChecker(ocspServer.getCertPathChecker());
		TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
		trustManagerFactory.init(new CertPathTrustManagerParameters(pkixBuilderParameters));
		SSLContext ctx = SSLContext.getInstance("TLSv1.2");
		ctx.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
		InetSocketAddress addr = new InetSocketAddress(port);
		HttpServer server = HttpServer.create(addr, ctx, false, true);
		handlers.forEach((context, handler) -> server.createContext(context, handler));
		server.start();
		if (Trace.isInfoEnabled())
			Trace.info("AutoHttpsServer start on " + addr);
		scheduler.schedule(() -> {
			if (Trace.isInfoEnabled())
				Trace.info("AutoHttpsServer restarting on expire.");
			try {
				server.stop();
				Thread.sleep(RESTART_DELAY_SECOND);
			} catch (Exception e) {
			}
			try {
				start(handlers);
			} catch (Exception e) {
				Trace.fatal("AutoHttpsServer fail", e);
			}
		}, chain[0].getNotAfter().getTime() - System.currentTimeMillis()
				- RESTART_MILLISECOND_BEFORE_CERTIFICATE_EXPIRE, TimeUnit.MILLISECONDS);
	}
}
