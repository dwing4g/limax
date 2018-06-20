package limax.pkix;

public interface X509RootCertificateParameter extends X509CertificateBaseParameter {
	default X509CertificatePolicyCPS getCertificatePolicyCPS() {
		return X509CertificatePolicyCPS.ROOT;
	}
}
