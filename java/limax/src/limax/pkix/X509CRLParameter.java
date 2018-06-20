package limax.pkix;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.Map;

public interface X509CRLParameter {
	X509Certificate getCACertificate();

	Map<BigInteger, Long> getRevokes();

	long getNextUpdateDelay();
}
