package limax.codec.asn1;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import limax.codec.Codec;
import limax.codec.CodecException;
import limax.codec.SinkStream;

public class ASN1Set extends ASN1Object implements ContainerObject {
	public static final ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier("2.1.0.0");
	public static final ASN1Tag tag = new ASN1Tag(TagClass.Universal, 17);

	private static class DERBlob implements Comparable<DERBlob> {
		private final byte[] data;

		public DERBlob(ASN1Object obj) throws CodecException {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			obj.render(new EncodeDER(new SinkStream(out)));
			data = out.toByteArray();
		}

		@Override
		public int compareTo(DERBlob o) {
			int len = Math.min(data.length, o.data.length);
			for (int i = 0; i < len; i++) {
				int l = data[i] & 0xff;
				int r = o.data[i] & 0xff;
				if (l < r)
					return -1;
				else if (l > r)
					return 1;
			}
			return data.length - o.data.length;
		}

		public void represent(Codec c) throws CodecException {
			c.update(data, 0, data.length);
		}

		public long getLength() {
			return data.length;
		}
	}

	private final ASN1ConstructedObject obj;

	public ASN1Set() {
		obj = new ASN1ConstructedObject(tag);
	}

	public ASN1Set(ASN1Tag tag) {
		obj = new ASN1ConstructedObject(tag);
	}

	public ASN1Set(ASN1Object... children) {
		obj = new ASN1ConstructedObject(tag, children);
	}

	public ASN1Set(ASN1Tag tag, ASN1Object... children) {
		obj = new ASN1ConstructedObject(tag, children);
	}

	public ASN1Set(List<ASN1Object> children) {
		obj = new ASN1ConstructedObject(tag, children);
	}

	public ASN1Set(ASN1Tag tag, List<ASN1Object> children) {
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
		if (c instanceof EncodeDER) {
			int i = 0;
			long length = 0;
			DERBlob[] objects = new DERBlob[size()];
			for (ASN1Object obj : getChildren())
				if (!(obj instanceof ASN1Optional && obj.isEmpty()))
					length += (objects[i++] = new DERBlob(obj)).getLength();
			Arrays.sort(objects);
			tag.render(c, false);
			byte[] blen = BERLength.render(length);
			c.update(blen, 0, blen.length);
			for (DERBlob b : objects)
				b.represent(c);
		} else {
			obj.render(c);
		}
	}

	@Override
	public String toString() {
		return "ASN1Set [" + size() + "]";
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
