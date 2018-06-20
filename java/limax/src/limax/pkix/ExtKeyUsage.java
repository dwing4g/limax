package limax.pkix;

import limax.codec.asn1.ASN1ObjectIdentifier;

public enum ExtKeyUsage {
	Any, ServerAuth, ClientAuth, CodeSigning, EmailProtection, TimeStamping, OCSPSigning;

	private final static ASN1ObjectIdentifier[] oids = new ASN1ObjectIdentifier[] {
			new ASN1ObjectIdentifier("2.5.29.37.0"), new ASN1ObjectIdentifier("1.3.6.1.5.5.7.3.1"),
			new ASN1ObjectIdentifier("1.3.6.1.5.5.7.3.2"), new ASN1ObjectIdentifier("1.3.6.1.5.5.7.3.3"),
			new ASN1ObjectIdentifier("1.3.6.1.5.5.7.3.4"), new ASN1ObjectIdentifier("1.3.6.1.5.5.7.3.8"),
			new ASN1ObjectIdentifier("1.3.6.1.5.5.7.3.9"), };

	ASN1ObjectIdentifier oid() {
		return oids[ordinal()];
	}
}
