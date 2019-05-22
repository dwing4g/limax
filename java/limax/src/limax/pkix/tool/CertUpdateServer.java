package limax.pkix.tool;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collections;
import java.util.Date;
import java.util.function.Function;

import javax.net.ssl.SSLSession;

import limax.http.DataSupplier;
import limax.http.Headers;
import limax.http.HttpExchange;
import limax.http.HttpHandler;
import limax.pkix.CAService;
import limax.pkix.X509CertificateRenewParameter;
import limax.util.SecurityUtils;
import limax.util.Trace;

class CertUpdateServer {
	private final CAService ca;
	private final int renewLifespanPercent;
	private final OcspServer ocspServer;
	private final Archive archive;
	private final AutoHttpsServer server;

	private class Handler implements HttpHandler {
		@Override
		public void censor(HttpExchange exchange) {
			exchange.getFormData().postLimit(CertServer.CERTFILE_MAX_SIZE);
		}

		@Override
		public DataSupplier handle(HttpExchange exchange) {
			try {
				SSLSession session = exchange.getSSLSession();
				X509Certificate peer = (X509Certificate) session.getPeerCertificates()[0];
				long notAfter = peer.getNotAfter().getTime();
				long notBefore = peer.getNotBefore().getTime();
				long now = System.currentTimeMillis();
				Headers headers = exchange.getResponseHeaders();
				if ((now - notBefore) * 100 > renewLifespanPercent * (notAfter - notBefore)) {
					String response = "";
					X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
							.generateCertificate(new ByteArrayInputStream(peer.getEncoded()));
					PublicKey publicKey = SecurityUtils.PublicKeyAlgorithm
							.loadPublicKey(new X509EncodedKeySpec(exchange.getFormData().getRaw().getBytes()));
					X509Certificate[] chain = ca.sign(new X509CertificateRenewParameter() {
						@Override
						public X509Certificate getCertificate() {
							return cert;
						}

						@Override
						public PublicKey getPublicKey() {
							return publicKey;
						}

						@Override
						public Function<X509Certificate, URI> getCRLDPMapping() {
							return cacert -> ocspServer.getCRLDP(cacert);
						}
					});
					archive.store(chain[0]);
					response = SecurityUtils.assemblePKCS7(chain);
					headers.set("Content-Type", "application/x-pkcs7-certificates");
					if (Trace.isInfoEnabled())
						Trace.info("CertUpdateServer renew [" + cert.getSubjectX500Principal() + "] expire at ["
								+ new Date(notAfter - notBefore + now) + "]");
					return DataSupplier.from(response, StandardCharsets.ISO_8859_1);
				}
			} catch (Exception e) {
			}
			return null;
		}
	}

	CertUpdateServer(CAService ca, int renewLifespanPercent, OcspServer ocspServer, Archive archive,
			AutoHttpsServer server) {
		this.ca = ca;
		this.renewLifespanPercent = renewLifespanPercent;
		this.ocspServer = ocspServer;
		this.archive = archive;
		this.server = server;
	}

	void start() throws Exception {
		server.start(Collections.singletonMap("/", new Handler()));
	}
}
