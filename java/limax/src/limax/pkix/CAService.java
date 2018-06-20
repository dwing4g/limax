package limax.pkix;

import java.net.URI;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.function.Function;

public interface CAService {
	X509Certificate[] sign(X509CertificateParameter param) throws Exception;

	X509Certificate[] sign(X509CertificateRenewParameter param) throws Exception;

	X509CRL sign(X509CRLParameter param) throws Exception;

	X509Certificate[] getCACertificates();

	X509Certificate getCACertificate();

	CAService combine(CAService other);

	static CAService create(URI location, X509RootCertificateParameter caparam,
			Function<String, char[]> passphraseCallback) throws Exception {
		return new CAServiceImpl(location, caparam, passphraseCallback);
	}

	static CAService create(URI location, Function<String, char[]> passphraseCallback) throws Exception {
		return create(location, null, passphraseCallback);
	}

	static Collection<String> getAlgorithms() {
		return CertificateAuthority.getAlgorithms();
	}
}
