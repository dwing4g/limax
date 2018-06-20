package limax.codec.asn1;

import java.io.ByteArrayOutputStream;
import java.util.Collection;

public class ASN1PrintableString extends ASN1String implements ConstructedObject {
	public static final ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("2.1.0.0.1.1");
	public static final ASN1Tag tag = new ASN1Tag(TagClass.Universal, 19);

	private static void verifyCharacter(byte ch) {
		if (ch >= 'A' && ch <= 'Z')
			return;
		if (ch >= 'a' && ch <= 'z')
			return;
		if (ch >= '0' && ch <= '9')
			return;
		if (ch == ' ' || ch == '\'' || ch == '(' || ch == ')' || ch == '+' || ch == ',' || ch == '-' || ch == '.'
				|| ch == '/' || ch == ':' || ch == '=' || ch == '?')
			return;
		throw new BERException("invalid PrintableString character[" + (char) ch + "]");
	}

	public ASN1PrintableString() {
		this(tag);
	}

	public ASN1PrintableString(ASN1Tag tag) {
		super(tag);
	}

	public ASN1PrintableString(String value) {
		this(tag, value);
	}

	public ASN1PrintableString(ASN1Tag tag, String value) {
		this(tag);
		setData(value.getBytes());
	}

	public String get() {
		return new String(getData());
	}

	@Override
	public void setData(byte[] value) {
		for (byte ch : value)
			verifyCharacter(ch);
		super.setData(value);
	}

	@Override
	public String toString() {
		return "ASN1PrintableString [" + get() + "]";
	}

	@Override
	public ASN1ObjectIdentifier getOID() {
		return oid;
	}

	@Override
	public void addChildren(Collection<ASN1Object> objects) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		objects.stream().map(obj -> ((ASN1PrintableString) obj).getData())
				.forEach(data -> out.write(data, 0, data.length));
		setData(out.toByteArray());
	}
}
