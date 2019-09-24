package limax.codec.asn1;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import limax.codec.Codec;
import limax.codec.CodecException;

public class ASN1UTCTime extends ASN1Object implements PrimitiveObject {
	public static final ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("2.1.0.0");
	public static final ASN1Tag tag = new ASN1Tag(TagClass.Universal, 23);
	private static final DateTimeFormatter utcFormat = DateTimeFormatter.ofPattern("yyMMddHHmmssX")
			.withZone(ZoneOffset.UTC);
	private final ASN1PrimitiveObject obj;

	public ASN1UTCTime(ASN1Tag tag, Instant value) {
		obj = new ASN1PrimitiveObject(tag, utcFormat.format(value).getBytes());
	}

	public ASN1UTCTime(Instant value) {
		this(tag, value);
	}

	public ASN1UTCTime(ASN1Tag tag) {
		this(tag, Instant.now());
	}

	public ASN1UTCTime() {
		this(tag, Instant.now());
	}

	public Instant get() {
		return parse(getData());
	}

	@Override
	public void setData(byte[] data) {
		parse(data);
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

	private static Instant parse(byte[] data) {
		try {
			return Instant.from(utcFormat.parse(new String(data)));
		} catch (Exception e) {
			throw new BERException(e);
		}
	}

	@Override
	public String toString() {
		return "ASN1UTCTime [" + parse(getData()) + "]";
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
