package limax.codec.asn1;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

public class ASN1BMPString extends ASN1String implements ConstructedObject {
	public static final ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("2.1.0.0");
	public static final ASN1Tag tag = new ASN1Tag(TagClass.Universal, 30);

	public ASN1BMPString() {
		this(tag);
	}

	public ASN1BMPString(ASN1Tag tag) {
		super(tag);
	}

	public ASN1BMPString(String value) {
		this(tag, value);
	}

	public ASN1BMPString(ASN1Tag tag, String value) {
		this(tag);
		setData(value.getBytes(StandardCharsets.UTF_16BE));
	}

	public String get() {
		return new String(getData(), StandardCharsets.UTF_16BE);
	}

	@Override
	public String toString() {
		return "ASN1BMPString [" + get() + "]";
	}

	@Override
	public ASN1ObjectIdentifier getOID() {
		return oid;
	}

	@Override
	public void addChildren(Collection<ASN1Object> objects) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		objects.stream().map(obj -> ((ASN1BMPString) obj).getData()).forEach(data -> out.write(data, 0, data.length));
		setData(out.toByteArray());
	}
}
