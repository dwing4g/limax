package limax.codec.asn1;

import java.util.Collection;

import limax.codec.CodecException;

interface ConstructedInput {
	void update(Collection<ASN1Object> objects) throws CodecException;
}
