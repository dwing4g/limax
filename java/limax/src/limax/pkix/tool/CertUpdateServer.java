package limax.pkix.tool;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collections;
import java.util.Date;
import java.util.function.Function;

import javax.net.ssl.SSLSession;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsExchange;

import limax.codec.Octets;
import limax.codec.SinkOctets;
import limax.codec.StreamSource;
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
		public void handle(HttpExchange exchange) throws IOException {
			SSLSession session = ((HttpsExchange) exchange).getSSLSession();
			X509Certificate peer = (X509Certificate) session.getPeerCertificates()[0];
			long notAfter = peer.getNotAfter().getTime();
			long notBefore = peer.getNotBefore().getTime();
			long now = System.currentTimeMillis();
			Headers headers = exchange.getResponseHeaders();
			if ((now - notBefore) * 100 > renewLifespanPercent * (notAfter - notBefore)) {
				String response = "";
				try (InputStream in = exchange.getRequestBody()) {
					X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
							.generateCertificate(new ByteArrayInputStream(peer.getEncoded()));
					Octets data = new Octets();
					new StreamSource(in, new SinkOctets(data)).flush();
					PublicKey publicKey = SecurityUtils.PublicKeyAlgorithm
							.loadPublicKey(new X509EncodedKeySpec(data.getBytes()));
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
				} catch (Exception e) {
				}
				exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length());
				try (OutputStream os = exchange.getResponseBody()) {
					os.write(response.getBytes());
				}
			} else {
				exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1);
			}
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
