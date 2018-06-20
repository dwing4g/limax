package limax.pkix;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

interface X509CertificateBaseParameter {
	X500Principal getSubject();

	Date getNotBefore();

	Date getNotAfter();

	X509CertificatePolicyCPS getCertificatePolicyCPS();

	default Collection<X509Extension> getAdditionalExtensions() {
		return Collections.emptyList();
	}
}
