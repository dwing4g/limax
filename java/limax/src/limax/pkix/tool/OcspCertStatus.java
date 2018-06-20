package limax.pkix.tool;

import java.math.BigInteger;

import limax.codec.asn1.ASN1Object;

class OcspCertStatus {
	private final ASN1Object certID;
	private final BigInteger serialNumber;
	private final Long revocationTime;

	OcspCertStatus(ASN1Object certID, BigInteger serialNumber, Long revocationTime) {
		this.certID = certID;
		this.serialNumber = serialNumber;
		this.revocationTime = revocationTime;
	}

	ASN1Object getCertID() {
		return certID;
	}

	BigInteger getSerialNumber() {
		return serialNumber;
	}

	Long getRevocationTime() {
		return revocationTime;
	}

	boolean isRevoked() {
		return revocationTime != null;
	}
}