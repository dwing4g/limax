package limax.codec.asn1;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import limax.codec.Codec;
import limax.codec.CodecException;

public class ASN1ConstructedObject extends ASN1Object implements ContainerObject {
	public static final ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("2.1.0.0");
	private static final byte[] UNKNOWNLENGTH = { (byte) 0x80, 0, 0 };

	private final ASN1Tag tag;
	private long length = -1;
	private final List<ASN1Object> children = new ArrayList<>();

	public ASN1ConstructedObject(ASN1Tag tag) {
		this.tag = tag;
	}

	public ASN1ConstructedObject(ASN1Tag tag, List<ASN1Object> children) {
		this.tag = tag;
		this.children.addAll(children);
	}

	public ASN1ConstructedObject(ASN1Tag tag, ASN1Object... children) {
		this.tag = tag;
		Collections.addAll(this.children, children);
	}

	@Override
	public ASN1Tag getTag() {
		return tag;
	}

	@Override
	public ASN1Object get(int index) {
		return children.get(index);
	}

	@Override
	public List<ASN1Object> getChildren() {
		return children;
	}

	@Override
	public int size() {
		return children.size();
	}

	@Override
	public void setLength(long length) {
		this.length = length;
	}

	@Override
	public void set(int index, ASN1Object object) {
		children.set(index, object);
	}

	@Override
	public void addChild(ASN1Object object) {
		children.add(object);
	}

	@Override
	public void addChildren(ASN1Object... objects) {
		Collections.addAll(children, objects);
	}

	@Override
	public void addChildren(Collection<ASN1Object> objects) {
		children.addAll(objects);
	}

	@Override
	public boolean isEmpty() {
		return children.stream().allMatch(ASN1Object::isEmpty);
	}

	@Override
	public long renderLength() {
		long length = children.stream().filter(obj -> !(obj instanceof ASN1Optional && obj.isEmpty()))
				.mapToLong(ASN1Object::renderLength).sum();
		setLength(length);
		return tag.renderLength() + BERLength.renderLength(length) + length;
	}

	@Override
	public void render(Codec c) throws CodecException {
		tag.render(c, false);
		if (c instanceof EncodeDER) {
			renderLength();
		} else {
			c.update(UNKNOWNLENGTH, 0, 1);
			for (ASN1Object obj : children)
				if (!(obj instanceof ASN1Optional && obj.isEmpty()))
					obj.render(c);
			c.update(UNKNOWNLENGTH, 1, 2);
			return;
		}
		byte[] blen = BERLength.render(length);
		c.update(blen, 0, blen.length);
		for (ASN1Object obj : children)
			if (!(obj instanceof ASN1Optional && obj.isEmpty()))
				obj.render(c);
	}

	@Override
	public String toString() {
		return String.format("ASN1ConstructedObject [%s, %d, %d]", tag, length, children.size());
	}

	@Override
	public ASN1ObjectIdentifier getOID() {
		return oid;
	}

	@Override
	public void visit(Visitor v) {
		super.visit(v);
		visitChildren(v);
	}

	protected void visitChildren(Visitor v) {
		v.enter();
		children.forEach(obj -> obj.visit(v));
		v.leave();
	}
}
