package limax.codec.asn1;

import limax.codec.Codec;
import limax.codec.CodecException;

public class ASN1RawData extends ASN1Object {
	private final byte[] data;

	public ASN1RawData(byte[] data) {
		this.data = data;
	}

	@Override
	public ASN1ObjectIdentifier getOID() {
		throw new BERException("ASN1RawData used for output only");
	}

	@Override
	public ASN1Tag getTag() {
		throw new BERException("ASN1RawData used for output only");
	}

	@Override
	public boolean isEmpty() {
		return data.length == 0;
	}

	@Override
	public long renderLength() {
		return data.length;
	}

	@Override
	public void render(Codec c) throws CodecException {
		c.update(data, 0, data.length);
	}

	@Override
	public String toString() {
		return "ASN1RawData [" + data.length + "]";
	}
}
