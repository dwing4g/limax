package limax.codec.asn1;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;

import limax.codec.Codec;
import limax.codec.CodecException;
import limax.util.Helper;

public class ASN1BitString extends ASN1Object implements PrimitiveObject, ConstructedObject {
	public static final ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("2.1.0.0");
	public static final ASN1Tag tag = new ASN1Tag(TagClass.Universal, 3);

	private final ASN1PrimitiveObject obj;

	public ASN1BitString() {
		obj = new ASN1PrimitiveObject(tag);
	}

	public ASN1BitString(ASN1Tag tag) {
		obj = new ASN1PrimitiveObject(tag);
	}

	private byte[] normalize(byte[] value) {
		byte[] encoded = new byte[value.length + 1];
		System.arraycopy(value, 0, encoded, 1, value.length);
		return encoded;
	}

	private byte[] normalize(byte[] value, int shift) {
		if (shift != 0)
			value = new BigInteger(1, value).shiftLeft(shift).toByteArray();
		if (value[0] != 0)
			value = normalize(value);
		value[0] = (byte) shift;
		return value;
	}

	private byte[] normalize(BitSet value) {
		return normalize(value.toByteArray(), -value.length() & 7);
	}

	public ASN1BitString(BitSet value) {
		obj = new ASN1PrimitiveObject(tag, normalize(value));
	}

	public ASN1BitString(ASN1Tag tag, BitSet value) {
		obj = new ASN1PrimitiveObject(tag, normalize(value));
	}

	public ASN1BitString(byte[] value) {
		obj = new ASN1PrimitiveObject(tag, normalize(value));
	}

	public ASN1BitString(ASN1Tag tag, byte[] value) {
		obj = new ASN1PrimitiveObject(tag, normalize(value));
	}

	@Override
	public void setData(byte[] data) {
		obj.setData(data);
	}

	@Override
	public byte[] getData() {
		return obj.getData();
	}

	public BitSet get() {
		byte[] encoded = getData();
		byte[] data = Arrays.copyOfRange(encoded, 1, encoded.length);
		int shift = encoded[0];
		return BitSet.valueOf(shift == 0 ? data : new BigInteger(1, data).shiftRight(shift).toByteArray());
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
		return "ASN1BitString [" + Helper.toHexString(getData()) + "]";
	}

	@Override
	public void addChildren(Collection<ASN1Object> objects) {
		BigInteger acc = BigInteger.ZERO;
		int count = 0;
		for (ASN1Object obj : objects) {
			ASN1BitString o = (ASN1BitString) obj;
			byte[] encoded = o.getData();
			byte[] data = Arrays.copyOfRange(encoded, 1, encoded.length);
			int shift = encoded[0];
			count += shift;
			acc = acc.shiftLeft(data.length * 8 - shift).or(new BigInteger(1, data).shiftRight(shift));
		}
		setData(normalize(acc.toByteArray(), count & 7));
	}
}
