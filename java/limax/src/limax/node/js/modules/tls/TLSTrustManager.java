package limax.node.js.modules.tls;

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import limax.node.js.modules.tls.TLSConfig.X509CertificateChainChecker;

class TLSTrustManager extends X509ExtendedTrustManager {
	private final X509ExtendedTrustManager pkixTrustManager;
	private final X509CertificateChainChecker trustChecker;
	private final boolean positiveCheck;

	private TLSTrustManager(TrustManager[] trustManagers, X509CertificateChainChecker trustChecker,
			boolean positiveCheck) throws Exception {
		for (TrustManager trustManager : trustManagers)
			if (trustManager instanceof X509ExtendedTrustManager) {
				this.pkixTrustManager = (X509ExtendedTrustManager) trustManager;
				this.trustChecker = trustChecker;
				this.positiveCheck = positiveCheck;
				return;
			}
		throw new Exception("no X509ExtendedTrustManager");
	}

	static TrustManager[] create(TrustManager[] trustManagers, X509CertificateChainChecker trustChecker,
			boolean positiveCheck) throws Exception {
		return new TrustManager[] { new TLSTrustManager(trustManagers, trustChecker, positiveCheck) };
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		throw new UnsupportedOperationException();
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return pkixTrustManager.getAcceptedIssuers();
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
			throws CertificateException {
		throw new UnsupportedOperationException();
	}

	private void trustCheck(X509Certificate[] chain, CertificateException exception) throws CertificateException {
		if (trustChecker != null) {
			try {
				if (trustChecker.accept(chain, exception))
					return;
			} catch (Exception e) {
				exception.addSuppressed(e);
			}
		}
		throw exception;
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
			throws CertificateException {
		if (positiveCheck) {
			trustCheck(chain, new CertificateException("PositiveCheck Needed"));
			return;
		}
		try {
			pkixTrustManager.checkClientTrusted(chain, authType, engine);
		} catch (CertificateException e) {
			trustCheck(chain, e);
		}
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
			throws CertificateException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
			throws CertificateException {
		if (positiveCheck) {
			trustCheck(chain, new CertificateException("PositiveCheck Needed"));
			return;
		}
		try {
			pkixTrustManager.checkServerTrusted(chain, authType, engine);
		} catch (CertificateException e) {
			trustCheck(chain, e);
		}
	}
}
