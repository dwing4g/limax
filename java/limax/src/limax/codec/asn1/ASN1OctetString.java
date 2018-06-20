package limax.codec.asn1;

import java.io.ByteArrayOutputStream;
import java.util.Collection;

import limax.codec.Codec;
import limax.codec.CodecException;
import limax.util.Helper;

public class ASN1OctetString extends ASN1Object implements PrimitiveObject, ConstructedObject {
	public static final ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("2.1.0.0");
	public static final ASN1Tag tag = new ASN1Tag(TagClass.Universal, 4);

	private final ASN1PrimitiveObject obj;

	public ASN1OctetString() {
		obj = new ASN1PrimitiveObject(tag);
	}

	public ASN1OctetString(ASN1Tag tag) {
		obj = new ASN1PrimitiveObject(tag);
	}

	public ASN1OctetString(byte[] data) {
		obj = new ASN1PrimitiveObject(tag, data);
	}

	public ASN1OctetString(ASN1Tag tag, byte[] data) {
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
		return "ASN1OctetString [" + Helper.toHexString(getData()) + "]";
	}

	@Override
	public ASN1ObjectIdentifier getOID() {
		return oid;
	}

	@Override
	public ASN1Tag getTag() {
		return obj.getTag();
	}

	@Override
	public void addChildren(Collection<ASN1Object> objects) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		objects.stream().map(obj -> ((ASN1OctetString) obj).getData()).forEach(data -> out.write(data, 0, data.length));
		setData(out.toByteArray());
	}
}
