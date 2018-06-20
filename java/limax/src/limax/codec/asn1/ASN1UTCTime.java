package limax.codec.asn1;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import limax.codec.Codec;
import limax.codec.CodecException;

public class ASN1UTCTime extends ASN1Object implements PrimitiveObject {
	public static final ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("2.1.0.0");
	public static final ASN1Tag tag = new ASN1Tag(TagClass.Universal, 23);
	private static final SimpleDateFormat utcFormat = new SimpleDateFormat("yyMMddHHmmss");

	static {
		utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		utcFormat.set2DigitYearStart(new Date(949363200000L));
	}

	private final ASN1PrimitiveObject obj;

	public ASN1UTCTime(ASN1Tag tag, Date value) {
		obj = new ASN1PrimitiveObject(tag, (utcFormat.format(value) + "Z").getBytes());
	}

	public ASN1UTCTime(Date value) {
		this(tag, value);
	}

	public ASN1UTCTime(ASN1Tag tag) {
		this(tag, new Date());
	}

	public ASN1UTCTime() {
		this(tag, new Date());
	}

	public Date get() {
		try {
			return utcFormat.parse(new String(getData()));
		} catch (ParseException e) {
			throw new BERException(e);
		}
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

	private static Date parse(byte[] data) {
		try {
			return utcFormat.parse(new String(data));
		} catch (ParseException e) {
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
