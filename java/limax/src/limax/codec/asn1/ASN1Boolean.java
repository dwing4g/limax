package limax.codec.asn1;

import java.util.Arrays;

import limax.codec.Codec;
import limax.codec.CodecException;

public class ASN1Boolean extends ASN1Object implements PrimitiveObject {
	public static final ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("2.1.0.0");
	public static final ASN1Tag tag = new ASN1Tag(TagClass.Universal, 1);
	private static final byte[] TRUE = new byte[] { (byte) 0xFF };
	private static final byte[] FALSE = new byte[] { 0 };

	private final ASN1PrimitiveObject obj;

	public ASN1Boolean() {
		obj = new ASN1PrimitiveObject(tag);
	}

	public ASN1Boolean(ASN1Tag tag) {
		obj = new ASN1PrimitiveObject(tag);
	}

	public ASN1Boolean(boolean value) {
		obj = new ASN1PrimitiveObject(tag, value ? TRUE : FALSE);
	}

	public ASN1Boolean(ASN1Tag tag, boolean value) {
		obj = new ASN1PrimitiveObject(tag, value ? TRUE : FALSE);
	}
	
	public boolean get() {
		return obj.getData()[0] == TRUE[0];
	}

	@Override
	public void setData(byte[] data) {
		if (data.length != 1)
			throw new BERException("invalid ASN1Boolean size = " + data.length);
		obj.setData(data[0] != 0 ? TRUE : FALSE);
	}

	@Override
	public byte[] getData() {
		return obj.getData();
	}

	@Override
	public ASN1Tag getTag() {
		return obj.getTag();
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
		return "ASN1Boolean [" + Arrays.equals(getData(), TRUE) + "]";
	}

	@Override
	public ASN1ObjectIdentifier getOID() {
		return oid;
	}
}
