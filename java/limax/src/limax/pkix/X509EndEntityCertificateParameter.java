package limax.pkix;

import java.util.Date;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

public interface X509EndEntityCertificateParameter extends X509CertificateParameter {
	default Date getNotBefore() {
		return new Date();
	}

	default Date getNotAfter() {
		return new Date(getNotBefore().getTime() + TimeUnit.DAYS.toMillis(30));
	}

	default EnumSet<KeyUsage> getKeyUsages() {
		return EnumSet.noneOf(KeyUsage.class);
	}

	default EnumSet<ExtKeyUsage> getExtKeyUsages() {
		return EnumSet.noneOf(ExtKeyUsage.class);
	}

	default X509CertificatePolicyCPS getCertificatePolicyCPS() {
		return X509CertificatePolicyCPS.EndEntity;
	}
}
