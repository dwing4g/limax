package limax.key;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsExchange;

import limax.codec.OctetsStream;
import limax.codec.SinkOctets;
import limax.codec.StreamSource;
import limax.util.Trace;

class KeyHandler implements HttpHandler {
	private final MasterKeyContainer masterKeyContainer;

	KeyHandler(MasterKeyContainer masterKeyContainer) {
		this.masterKeyContainer = masterKeyContainer;
	}

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		KeyIdent keyIdent;
		try (InputStream in = exchange.getRequestBody()) {
			OctetsStream os = new OctetsStream();
			new StreamSource(in, new SinkOctets(os)).flush();
			keyIdent = new KeyIdent(os);
		} catch (Exception e) {
			throw new IOException(e);
		}
		X509Certificate peer = (X509Certificate) ((HttpsExchange) exchange).getSSLSession().getPeerCertificates()[0];
		Function<KeyIdent, String> log = _keyIdent -> "KeyRequest "
				+ exchange.getRemoteAddress().getAddress().getHostAddress() + " " + peer.getSubjectX500Principal() + " "
				+ _keyIdent;
		if (KeyAllocator.containsURI(peer, URI.create(keyIdent.getGroup()))) {
			MasterKeyContainer.Digester digester = null;
			long timestamp = keyIdent.getTimestamp();
			if (timestamp != -1)
				digester = masterKeyContainer.getDigester(timestamp);
			if (digester == null)
				digester = masterKeyContainer.getDigester();
			OctetsStream os = new OctetsStream().marshal(digester.sign(keyIdent, peer));
			exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
			exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, os.size());
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(os.array(), 0, os.size());
			}
			if (Trace.isInfoEnabled())
				Trace.info(log.apply(keyIdent));
		} else {
			exchange.sendResponseHeaders(HttpURLConnection.HTTP_BAD_REQUEST, -1);
			if (Trace.isWarnEnabled())
				Trace.warn(log.apply(keyIdent));
		}
	}
}