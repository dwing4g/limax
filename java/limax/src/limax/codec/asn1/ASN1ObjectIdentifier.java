package limax.codec.asn1;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Arrays;

import limax.codec.Codec;
import limax.codec.CodecException;

public class ASN1ObjectIdentifier extends ASN1Object implements PrimitiveObject {
	public static final ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("2.1.0.0.0.1");
	public static final ASN1Tag tag = new ASN1Tag(TagClass.Universal, 6);

	private final ASN1PrimitiveObject obj;
	private volatile String rep;

	public ASN1ObjectIdentifier() {
		this(tag);
	}

	public ASN1ObjectIdentifier(ASN1Tag tag) {
		obj = new ASN1PrimitiveObject(tag);
	}

	public ASN1ObjectIdentifier(String id) {
		this(tag, id);
	}

	public ASN1ObjectIdentifier(ASN1Tag tag, String id) {
		obj = new ASN1PrimitiveObject(tag, encode(id));
		rep = id;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof ASN1ObjectIdentifier) {
			ASN1ObjectIdentifier o = (ASN1ObjectIdentifier) obj;
			return Arrays.equals(getData(), o.getData());
		}
		return false;
	}

	@Override
	public void setData(byte[] data) {
		obj.setData(data);
		rep = null;
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
		return "ASN1ObjectIdentifier [" + get() + "]";
	}

	@Override
	public ASN1ObjectIdentifier getOID() {
		return oid;
	}

	@Override
	public ASN1Tag getTag() {
		return tag;
	}

	public String get() {
		return rep != null ? rep : (rep = decode());
	}

	public void set(String oid) {
		setData(encode(oid));
		rep = oid;
	}

	private static byte[] encode(String id) {
		String s[] = id.split("\\.");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] tmp = new byte[32];
		int k;
		long add = Long.parseLong(s[0]) * 40;
		for (int i = 1; i < s.length; i++) {
			k = 0;
			try {
				long n = Long.parseLong(s[i]) + add;
				do {
					tmp[k++] = (byte) (n & 0x7f);
					n >>= 7;
				} while (n != 0);
			} catch (NumberFormatException e) {
				BigInteger n = new BigInteger(s[i]);
				if (add > 0)
					n = n.add(BigInteger.valueOf(add));
				int cb = (n.bitLength() + 6) / 7;
				if (cb >= tmp.length)
					tmp = new byte[cb + 32];
				BigInteger divider = BigInteger.valueOf(128);
				while (cb-- > 0) {
					BigInteger d[] = n.divideAndRemainder(divider);
					n = d[0];
					tmp[k++] = d[1].toByteArray()[0];
				}
			}
			while (--k > 0)
				out.write(tmp[k] | 0x80);
			out.write(tmp[k]);
			add = 0;
		}
		return out.toByteArray();
	}

	private String decode() {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		long n = 0;
		byte[] value = getData();
		for (int i = 0; i < value.length; i++) {
			if (value[i] >= 0) {
				n = (n << 7) + value[i];
				if (first) {
					if (n < 80)
						sb.append(n / 40).append(".").append(n % 40).append(".");
					else
						sb.append(2).append(".").append(n - 80).append(".");
					first = false;
				} else {
					sb.append(n).append(".");
				}
				n = 0;
			} else {
				n = (n << 7) + (value[i] & 0x7F);
			}
		}
		sb.setLength(sb.length() - 1);
		return sb.toString();
	}
}
