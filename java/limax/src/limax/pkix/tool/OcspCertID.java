package limax.pkix.tool;

import java.math.BigInteger;

import limax.codec.asn1.ASN1Integer;
import limax.codec.asn1.ASN1Object;
import limax.codec.asn1.ASN1ObjectIdentifier;
import limax.codec.asn1.ASN1OctetString;
import limax.codec.asn1.ASN1Sequence;

class OcspCertID {
	private final ASN1Object certID;
	private final OcspIssuerKey issuerKey;
	private final BigInteger serialNumber;

	OcspCertID(ASN1Sequence obj) throws Exception {
		String hashAlgorithm = ((ASN1ObjectIdentifier) ((ASN1Sequence) obj.get(0)).get(0)).get();
		byte[] issuerNameHash = ((ASN1OctetString) obj.get(1)).get();
		byte[] issuerKeyHash = ((ASN1OctetString) obj.get(2)).get();
		this.certID = obj;
		this.issuerKey = new OcspIssuerKey(hashAlgorithm, issuerNameHash, issuerKeyHash);
		this.serialNumber = ((ASN1Integer) obj.get(3)).get();
	}

	OcspIssuerKey getIssuerKey() {
		return issuerKey;
	}

	BigInteger getSerialNumber() {
		return serialNumber;
	}

	ASN1Object getCertID() {
		return certID;
	}
}