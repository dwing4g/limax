package limax.codec.asn1;

import limax.codec.Codec;
import limax.codec.CodecException;

public class ASN1Null extends ASN1Object implements PrimitiveObject {
	public static final ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("2.1.0.0");
	public static final ASN1Tag tag = new ASN1Tag(TagClass.Universal, 5);

	private final ASN1PrimitiveObject obj;

	public ASN1Null(ASN1Tag tag) {
		obj = new ASN1PrimitiveObject(tag);
	}

	public ASN1Null() {
		this(tag);
	}

	public Object get() {
		return null;
	}

	@Override
	public void setData(byte[] data) {
		if (data.length != 0)
			throw new BERException("invalid ASN1Null size = " + data.length);
		obj.setData(data);
	}

	@Override
	public byte[] getData() {
		return obj.getData();
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public long renderLength() {
		return obj.renderLength();
	}

	@Override
	public void render(Codec c) throws CodecException {
		obj.render(c);
	}

	@Override
	public String toString() {
		return "ASN1Null";
	}

	@Override
	public ASN1ObjectIdentifier getOID() {
		return oid;
	}

	@Override
	public ASN1Tag getTag() {
		return tag;
	}
}
