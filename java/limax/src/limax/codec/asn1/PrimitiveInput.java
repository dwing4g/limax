package limax.codec.asn1;

import limax.codec.CodecException;

interface PrimitiveInput {
	void update(byte[] data, int off, int len) throws CodecException;
}
