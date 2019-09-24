package limax.codec.asn1;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import limax.codec.CodecException;

class BERObject {
	private static final Map<ASN1Tag, Class<? extends ASN1Object>> tag2Class = new HashMap<>();
	static {
		tag2Class.put(ASN1Boolean.tag, ASN1Boolean.class);
		tag2Class.put(ASN1Integer.tag, ASN1Integer.class);
		tag2Class.put(ASN1BitString.tag, ASN1BitString.class);
		tag2Class.put(ASN1OctetString.tag, ASN1OctetString.class);
		tag2Class.put(ASN1Null.tag, ASN1Null.class);
		tag2Class.put(ASN1ObjectIdentifier.tag, ASN1ObjectIdentifier.class);
		tag2Class.put(ASN1IA5String.tag, ASN1IA5String.class);
		tag2Class.put(ASN1UTF8String.tag, ASN1UTF8String.class);
		tag2Class.put(ASN1PrintableString.tag, ASN1PrintableString.class);
		tag2Class.put(ASN1UTCTime.tag, ASN1UTCTime.class);
		tag2Class.put(ASN1GeneralizedTime.tag, ASN1GeneralizedTime.class);
		tag2Class.put(ASN1Enumerated.tag, ASN1Enumerated.class);
		tag2Class.put(ASN1Sequence.tag, ASN1Sequence.class);
		tag2Class.put(ASN1Set.tag, ASN1Set.class);
	}

	private BERIdentifier berIdentifier;
	private BERLength berLength;
	private ObjectBuilder objectBuilder;

	public BERStage init() {
		berIdentifier = new BERIdentifier();
		berLength = new BERLength();
		return BERStage.IDENTIFIER;
	}

	public BERStage identifierNext(byte in) {
		return berIdentifier.next(in);
	}

	public BERStage lengthNext(byte in) {
		return berLength.next(in);
	}

	protected ASN1Object tag2Object(ASN1Tag tag) {
		try {
			return tag2Class.get(tag).getDeclaredConstructor().newInstance();
		} catch (Exception e) {
			return berIdentifier.isConstructed() ? new ASN1ConstructedObject(tag) : new ASN1PrimitiveObject(tag);
		}
	}

	protected DecodeBER wrapDecoder(ASN1Object obj) {
		if (berIdentifier.isConstructed()) {
			objectBuilder = new ConstructedBuilder((ConstructedObject) obj, getLength());
			return new DecodeBER(new BERObject());
		} else {
			objectBuilder = new PrimitiveBuilder((PrimitiveObject) obj);
			return null;
		}
	}

	public DecodeBER getReady() {
		return wrapDecoder(tag2Object(getTag()));
	}

	public void update(byte data[], int off, int len) throws CodecException {
		((PrimitiveInput) objectBuilder).update(data, off, len);
	}

	public void update(Collection<ASN1Object> objects) throws CodecException {
		((ConstructedInput) objectBuilder).update(objects);
	}

	public ASN1Object endorse() throws CodecException {
		return objectBuilder.endorse();
	}

	protected void setObjectBuilder(ObjectBuilder objectBuilder) {
		this.objectBuilder = objectBuilder;
	}

	protected ASN1Tag getTag() {
		return berIdentifier.getTag();
	}

	protected boolean isConstructed() {
		return berIdentifier.isConstructed();
	}

	protected long getLength() {
		return berLength.getLength();
	}
}