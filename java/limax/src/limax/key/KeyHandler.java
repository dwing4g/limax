package limax.key;

import java.net.HttpURLConnection;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.function.Function;

import limax.codec.OctetsStream;
import limax.http.DataSupplier;
import limax.http.HttpException;
import limax.http.HttpExchange;
import limax.http.HttpHandler;
import limax.util.Trace;

class KeyHandler implements HttpHandler {
	private final MasterKeyContainer masterKeyContainer;

	KeyHandler(MasterKeyContainer masterKeyContainer) {
		this.masterKeyContainer = masterKeyContainer;
	}

	@Override
	public void censor(HttpExchange exchange) {
		exchange.getFormData().postLimit(4096);
	}

	@Override
	public DataSupplier handle(HttpExchange exchange) {
		try {
			KeyIdent keyIdent = new KeyIdent(OctetsStream.wrap(exchange.getFormData().getRaw()));
			X509Certificate peer = (X509Certificate) exchange.getSSLSession().getPeerCertificates()[0];
			Function<KeyIdent, String> log = _keyIdent -> "KeyRequest "
					+ exchange.getPeerAddress().getAddress().getHostAddress() + " " + peer.getSubjectX500Principal()
					+ " " + _keyIdent;
			if (KeyAllocator.containsURI(peer, URI.create(keyIdent.getGroup()))) {
				MasterKeyContainer.Digester digester = null;
				long timestamp = keyIdent.getTimestamp();
				if (timestamp != -1)
					digester = masterKeyContainer.getDigester(timestamp);
				if (digester == null)
					digester = masterKeyContainer.getDigester();
				OctetsStream os = new OctetsStream().marshal(digester.sign(keyIdent, peer));
				exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
				if (Trace.isInfoEnabled())
					Trace.info(log.apply(keyIdent));
				return DataSupplier.from(os.getByteBuffer());
			} else {
				if (Trace.isWarnEnabled())
					Trace.warn(log.apply(keyIdent));
			}
		} catch (Exception e) {
			if (Trace.isErrorEnabled())
				Trace.error("KeyRequest", e);
		}
		throw new HttpException(HttpURLConnection.HTTP_BAD_REQUEST, true);
	}
}