package limax.codec.asn1;

import limax.codec.Codec;
import limax.codec.CodecException;

public class ASN1Optional extends ASN1Object {
	private final ASN1Object obj;

	public ASN1Optional(ASN1Object obj) {
		this.obj = obj;
	}

	@Override
	public ASN1ObjectIdentifier getOID() {
		return obj.getOID();
	}

	public ASN1Object get() {
		return obj;
	}
	
	@Override
	public ASN1Tag getTag() {
		return obj.getTag();
	}

	@Override
	public boolean compareTag(ASN1Tag tag) {
		return obj.compareTag(tag);
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
	public void visit(Visitor v) {
		if (!isEmpty())
			obj.visit(v);
	}

	@Override
	public String toString() {
		return obj.toString();
	}
}
