package limax.codec.asn1;

import limax.codec.Codec;
import limax.codec.CodecException;

public class ASN1PrimitiveObject extends ASN1Object implements PrimitiveObject {
	public static final ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("2.1.0.0");
	private static final byte[] NULL = new byte[0];

	private final ASN1Tag tag;
	private byte[] data;

	public ASN1PrimitiveObject(ASN1Tag tag) {
		this(tag, NULL);
	}

	public ASN1PrimitiveObject(ASN1Tag tag, byte[] data) {
		this.tag = tag;
		this.data = data;
	}

	@Override
	public ASN1Tag getTag() {
		return tag;
	}

	@Override
	public void setData(byte[] data) {
		this.data = data;
	}

	@Override
	public byte[] getData() {
		return data;
	}

	@Override
	public boolean isEmpty() {
		return data.length == 0;
	}

	@Override
	public long renderLength() {
		return tag.renderLength() + BERLength.renderLength(data.length) + data.length;
	}

	@Override
	public void render(Codec c) throws CodecException {
		tag.render(c, true);
		byte[] blen = BERLength.render(data.length);
		c.update(blen, 0, blen.length);
		c.update(data, 0, data.length);
	}

	@Override
	public String toString() {
		return String.format("ASN1PrimitiveObject [%s, %d]", tag, data.length);
	}

	@Override
	public ASN1ObjectIdentifier getOID() {
		return oid;
	}
}
