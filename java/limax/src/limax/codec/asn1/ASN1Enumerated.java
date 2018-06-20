package limax.codec.asn1;

import limax.codec.Codec;
import limax.codec.CodecException;
import limax.util.Helper;

public class ASN1Enumerated extends ASN1Object implements PrimitiveObject {
	public static final ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("2.1.0.0");
	public static final ASN1Tag tag = new ASN1Tag(TagClass.Universal, 10);

	private final ASN1PrimitiveObject obj;

	public ASN1Enumerated() {
		obj = new ASN1PrimitiveObject(tag);
	}

	public ASN1Enumerated(ASN1Tag tag) {
		obj = new ASN1PrimitiveObject(tag);
	}

	public ASN1Enumerated(byte[] data) {
		obj = new ASN1PrimitiveObject(tag, data);
	}

	public ASN1Enumerated(ASN1Tag tag, byte[] data) {
		obj = new ASN1PrimitiveObject(tag, data);
	}

	public byte[] get() {
		return getData();
	}

	@Override
	public void setData(byte[] data) {
		obj.setData(data);
	}

	@Override
	public byte[] getData() {
		return obj.getData();
	}

	@Override
	public boolean isEmpty() {
		return obj.isEmpty();
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
		return "ASN1Enumerated [" + Helper.toHexString(getData()) + "]";
	}

	@Override
	public ASN1ObjectIdentifier getOID() {
		return oid;
	}

	@Override
	public ASN1Tag getTag() {
		return obj.getTag();
	}
}
