package limax.codec.asn1;

import java.io.ByteArrayOutputStream;

import limax.codec.CodecException;
import limax.codec.Pump;
import limax.codec.SinkStream;

public abstract class ASN1Object implements Pump {
	public abstract ASN1ObjectIdentifier getOID();

	public abstract ASN1Tag getTag();

	public abstract boolean isEmpty();

	public abstract long renderLength();

	public boolean compareTag(ASN1Tag tag) {
		return getTag().equals(tag);
	}

	public void visit(Visitor v) {
		v.update(this);
	}

	public byte[] toDER() throws CodecException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		render(new EncodeDER(new SinkStream(baos)));
		return baos.toByteArray();
	}

	public byte[] toBER() throws CodecException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		render(new SinkStream(baos));
		return baos.toByteArray();
	}
}
