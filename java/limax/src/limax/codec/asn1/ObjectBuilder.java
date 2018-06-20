package limax.codec.asn1;

import limax.codec.CodecException;

interface ObjectBuilder {
	ASN1Object endorse() throws CodecException;
}
