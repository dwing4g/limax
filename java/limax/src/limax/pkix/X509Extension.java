package limax.pkix;

import limax.codec.asn1.ASN1Boolean;
import limax.codec.asn1.ASN1ObjectIdentifier;
import limax.codec.asn1.ASN1RawData;
import limax.codec.asn1.ASN1Sequence;

public class X509Extension {
	private final String oid;
	private final boolean critical;
	private final byte[] extnValue;

	public X509Extension(String oid, boolean critical, byte[] extnValue) {
		this.oid = oid;
		this.critical = critical;
		this.extnValue = extnValue;
	}

	ASN1Sequence assemble() {
		ASN1Sequence extension = new ASN1Sequence();
		extension.addChild(new ASN1ObjectIdentifier(oid));
		if (critical)
			extension.addChild(new ASN1Boolean(true));
		extension.addChild(new ASN1RawData(extnValue));
		return extension;
	}

	String getOID() {
		return oid;
	}
}
