package limax.pkix;

import java.net.URI;
import java.util.Arrays;

import limax.codec.CodecException;
import limax.codec.asn1.ASN1IA5String;
import limax.codec.asn1.ASN1Object;
import limax.codec.asn1.ASN1ObjectIdentifier;
import limax.codec.asn1.ASN1OctetString;
import limax.codec.asn1.ASN1Sequence;

public enum X509CertificatePolicyCPS {
	ROOT {
		@Override
		protected URI cPSURI() {
			return cPSURIROOT;
		}
	},
	CA {
		@Override
		protected URI cPSURI() {
			return cPSURICA;
		}
	},
	EndEntity {
		@Override
		protected URI cPSURI() {
			return cPSURIEndEntity;
		}
	};

	private static final ASN1ObjectIdentifier OID_AnyPolicy = new ASN1ObjectIdentifier("2.5.29.32.0");
	private static final ASN1ObjectIdentifier OID_PolicyQualifierCPS = new ASN1ObjectIdentifier("1.3.6.1.5.5.7.2.1");
	private static final URI cPSURIROOT = URI.create("http://pkix.limax-project.org/root/cps");
	private static final URI cPSURICA = URI.create("http://pkix.limax-project.org/ca/cps");
	private static final URI cPSURIEndEntity = URI.create("http://pkix.limax-project.org/endentity/cps");

	protected abstract URI cPSURI();

	ASN1Object toPolicyQualifierInfo() throws CodecException {
		return new ASN1OctetString(new ASN1Sequence(new ASN1Sequence(OID_AnyPolicy,
				new ASN1Sequence(new ASN1Sequence(OID_PolicyQualifierCPS, new ASN1IA5String(toString()))))).toDER());
	}

	@Override
	public String toString() {
		return cPSURI().toASCIIString();
	}

	public boolean equals(byte[] extnValue) {
		try {
			return Arrays.equals(extnValue, toPolicyQualifierInfo().toDER());
		} catch (CodecException e) {
			return false;
		}
	}
}
