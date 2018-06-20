package limax.codec.asn1;

import java.util.Collection;

import limax.codec.CodecException;

interface ConstructedObject {
	void addChildren(Collection<ASN1Object> objects) throws CodecException;
}
