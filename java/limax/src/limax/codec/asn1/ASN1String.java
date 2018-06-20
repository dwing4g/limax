package limax.codec.asn1;

import limax.codec.Codec;
import limax.codec.CodecException;

public abstract class ASN1String extends ASN1Object implements PrimitiveObject {
	private final ASN1PrimitiveObject obj;

	protected ASN1String(ASN1Tag tag) {
		obj = new ASN1PrimitiveObject(tag);
	}

	public abstract String get();

	@Override
	public void setData(byte[] data) {
		obj.setData(data);
	}

	@Override
	public final byte[] getData() {
		return obj.getData();
	}

	@Override
	public final boolean isEmpty() {
		return obj.isEmpty();
	}

	@Override
	public final long renderLength() {
		return obj.renderLength();
	}

	@Override
	public final void render(Codec c) throws CodecException {
		obj.render(c);
	}

	@Override
	public final ASN1Tag getTag() {
		return obj.getTag();
	}
}
