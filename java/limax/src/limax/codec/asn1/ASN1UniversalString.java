package limax.codec.asn1;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Collection;

public class ASN1UniversalString extends ASN1String implements ConstructedObject {
	private static final Charset charset = Charset.forName("UTF-32BE");
	public static final ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("2.1.0.0");
	public static final ASN1Tag tag = new ASN1Tag(TagClass.Universal, 28);

	public ASN1UniversalString() {
		this(tag);
	}

	public ASN1UniversalString(ASN1Tag tag) {
		super(tag);
	}

	public ASN1UniversalString(String value) {
		this(tag, value);
	}

	public ASN1UniversalString(ASN1Tag tag, String value) {
		this(tag);
		setData(value.getBytes(charset));
	}

	public String get() {
		return new String(getData(), charset);
	}

	@Override
	public String toString() {
		return "ASN1UniversalString [" + get() + "]";
	}

	@Override
	public ASN1ObjectIdentifier getOID() {
		return oid;
	}

	@Override
	public void addChildren(Collection<ASN1Object> objects) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		objects.stream().map(obj -> ((ASN1UniversalString) obj).getData())
				.forEach(data -> out.write(data, 0, data.length));
		setData(out.toByteArray());
	}
}
