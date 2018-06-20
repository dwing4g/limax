package limax.codec.asn1;

import limax.codec.Codec;
import limax.codec.CodecException;

public class ASN1Any extends ASN1Object {
	private ASN1Object obj;

	public ASN1Any() {
		this(null);
	}

	public ASN1Any(ASN1Object obj) {
		this.obj = obj;
	}

	@Override
	public ASN1ObjectIdentifier getOID() {
		if (obj == null)
			throw new BERException("ASN1Any must initialize object entity");
		return obj.getOID();
	}

	@Override
	public ASN1Tag getTag() {
		if (obj == null)
			throw new BERException("ASN1Any must initialize object entity");
		return obj.getTag();
	}

	@Override
	public boolean isEmpty() {
		return obj != null ? obj.isEmpty() : true;
	}

	@Override
	public long renderLength() {
		return obj != null ? obj.renderLength() : 0;
	}

	@Override
	public void render(Codec c) throws CodecException {
		if (obj != null)
			obj.render(c);
	}

	@Override
	public boolean compareTag(ASN1Tag tag) {
		return obj == null ? true : obj.compareTag(tag);
	}

	@Override
	public void visit(Visitor v) {
		if (obj == null)
			super.visit(v);
		else
			obj.visit(v);
	}

	@Override
	public String toString() {
		return obj == null ? "ASN1Any [uninitialized]" : obj.toString();
	}

	public void set(ASN1Object obj) {
		this.obj = obj;
	}

	public ASN1Object get() {
		return obj;
	}
}
