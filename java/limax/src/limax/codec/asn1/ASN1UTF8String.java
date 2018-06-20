package limax.codec.asn1;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

public class ASN1UTF8String extends ASN1String implements ConstructedObject {
	public static final ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("2.1.0.0");
	public static final ASN1Tag tag = new ASN1Tag(TagClass.Universal, 12);

	public ASN1UTF8String() {
		this(tag);
	}

	public ASN1UTF8String(ASN1Tag tag) {
		super(tag);
	}

	public ASN1UTF8String(String value) {
		this(tag, value);
	}

	public ASN1UTF8String(ASN1Tag tag, String value) {
		this(tag);
		setData(value.getBytes(StandardCharsets.UTF_8));
	}

	public String get() {
		return new String(getData(), StandardCharsets.UTF_8);
	}

	@Override
	public String toString() {
		return "ASN1UTF8String [" + get() + "]";
	}

	@Override
	public ASN1ObjectIdentifier getOID() {
		return oid;
	}

	@Override
	public void addChildren(Collection<ASN1Object> objects) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		objects.stream().map(obj -> ((ASN1UTF8String) obj).getData()).forEach(data -> out.write(data, 0, data.length));
		setData(out.toByteArray());
	}
}
