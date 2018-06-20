package limax.pkix;

import java.net.URI;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.function.Function;

public interface X509CertificateRenewParameter {
	X509Certificate getCertificate();

	default PublicKey getPublicKey() {
		return getCertificate().getPublicKey();
	}

	Function<X509Certificate, URI> getCRLDPMapping();
}
