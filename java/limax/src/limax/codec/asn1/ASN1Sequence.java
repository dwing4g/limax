package limax.codec.asn1;

import java.util.Collection;
import java.util.List;

import limax.codec.Codec;
import limax.codec.CodecException;

public class ASN1Sequence extends ASN1Object implements ContainerObject {
	public static final ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("2.1.0.0");
	public static final ASN1Tag tag = new ASN1Tag(TagClass.Universal, 16);

	private final ASN1ConstructedObject obj;

	public ASN1Sequence() {
		obj = new ASN1ConstructedObject(tag);
	}

	public ASN1Sequence(ASN1Tag tag) {
		obj = new ASN1ConstructedObject(tag);
	}

	public ASN1Sequence(ASN1Object... children) {
		obj = new ASN1ConstructedObject(tag, children);
	}

	public ASN1Sequence(ASN1Tag tag, ASN1Object... children) {
		obj = new ASN1ConstructedObject(tag, children);
	}

	public ASN1Sequence(List<ASN1Object> children) {
		obj = new ASN1ConstructedObject(tag, children);
	}

	public ASN1Sequence(ASN1Tag tag, List<ASN1Object> children) {
		obj = new ASN1ConstructedObject(tag, children);
	}

	@Override
	public void setLength(long length) {
		obj.setLength(length);
	}

	@Override
	public void set(int index, ASN1Object object) {
		obj.set(index, object);
	}

	@Override
	public void addChild(ASN1Object objects) {
		obj.addChild(objects);
	}

	@Override
	public void addChildren(ASN1Object... objects) {
		obj.addChildren(objects);
	}

	@Override
	public void addChildren(Collection<ASN1Object> objects) {
		obj.addChildren(objects);
	}

	@Override
	public ASN1Object get(int index) {
		return obj.get(index);
	}

	@Override
	public List<ASN1Object> getChildren() {
		return obj.getChildren();
	}

	@Override
	public int size() {
		return obj.size();
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
	public String toString() {
		return "ASN1Sequence [" + size() + "]";
	}

	@Override
	public ASN1ObjectIdentifier getOID() {
		return oid;
	}

	@Override
	public ASN1Tag getTag() {
		return obj.getTag();
	}

	@Override
	public void visit(Visitor v) {
		super.visit(v);
		obj.visitChildren(v);
	}
}
