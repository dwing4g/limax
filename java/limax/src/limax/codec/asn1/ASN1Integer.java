package limax.codec.asn1;

import java.math.BigInteger;

import limax.codec.Codec;
import limax.codec.CodecException;

public class ASN1Integer extends ASN1Object implements PrimitiveObject {
	public static final ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("2.1.0.0");
	public static final ASN1Tag tag = new ASN1Tag(TagClass.Universal, 2);

	private final ASN1PrimitiveObject obj;

	public ASN1Integer() {
		obj = new ASN1PrimitiveObject(tag);
	}

	public ASN1Integer(ASN1Tag tag) {
		obj = new ASN1PrimitiveObject(tag);
	}

	public ASN1Integer(BigInteger value) {
		obj = new ASN1PrimitiveObject(tag, value.toByteArray());
	}

	public ASN1Integer(ASN1Tag tag, BigInteger value) {
		obj = new ASN1PrimitiveObject(tag, value.toByteArray());
	}

	public BigInteger get() {
		return new BigInteger(getData());
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
		StringBuilder sb = new StringBuilder("ASN1Integer [");
		for (byte b : getData())
			sb.append(String.format("%02x", b));
		return sb.append("]").toString();
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
