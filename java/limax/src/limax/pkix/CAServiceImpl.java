package limax.pkix;

import java.net.URI;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import limax.util.SecurityUtils;

class CAServiceImpl implements CAService {
	private final CertificateAuthority ca;
	private final Set<CertificateAuthority> cas;

	CAServiceImpl(URI location, X509RootCertificateParameter x509, Function<String, char[]> passphraseCallback)
			throws Exception {
		this.ca = new CertificateAuthority(location, x509, passphraseCallback);
		this.cas = Collections.singleton(ca);
	}

	CAServiceImpl(CAServiceImpl service0, CAServiceImpl service1) {
		Object subject0 = service0.ca.getCACertificate().getSubjectX500Principal();
		Object subject1 = service1.ca.getCACertificate().getSubjectX500Principal();
		if (!subject0.equals(subject1))
			throw new IllegalArgumentException(
					"combined CAService contains different subject. [" + subject0 + "] vs [" + subject1 + "]");
		this.cas = new HashSet<>(service0.cas);
		this.cas.addAll(service1.cas);
		long max = Long.MIN_VALUE;
		CertificateAuthority newest = null;
		for (CertificateAuthority ca : cas) {
			long date = ca.getCACertificate().getNotAfter().getTime();
			if (max < date) {
				max = date;
				newest = ca;
			}
		}
		this.ca = newest;
	}

	@Override
	public X509Certificate[] sign(X509CertificateParameter param) throws Exception {
		return ca.sign(param);
	}

	@Override
	public CAService combine(CAService other) {
		return new CAServiceImpl(this, (CAServiceImpl) other);
	}

	@Override
	public X509Certificate[] getCACertificates() {
		return cas.stream().map(CertificateAuthority::getCACertificate).toArray(X509Certificate[]::new);
	}

	@Override
	public X509Certificate getCACertificate() {
		return ca.getCACertificate();
	}

	@Override
	public X509Certificate[] sign(X509CertificateRenewParameter param) throws Exception {
		for (CertificateAuthority ca : cas)
			if (SecurityUtils.isSignedBy(param.getCertificate(), ca.getCACertificate()))
				return this.ca.sign(param);
		throw new CertificateException("Renewing Certificate not signed by the CAService");
	}

	@Override
	public X509CRL sign(X509CRLParameter param) throws Exception {
		for (CertificateAuthority ca : cas)
			if (ca.getCACertificate().equals(param.getCACertificate()))
				return ca.sign(param);
		throw new CRLException("No corresponding cacert in the CAService");
	}
}