package limax.pkix;

public interface X509CACertificateParameter extends X509CertificateParameter {
	default int getBasicConstraints() {
		return 0;
	}

	default X509CertificatePolicyCPS getCertificatePolicyCPS() {
		return X509CertificatePolicyCPS.CA;
	}
}
