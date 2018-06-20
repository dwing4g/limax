package limax.codec.asn1;

import java.io.ByteArrayOutputStream;
import java.util.Collection;

import limax.codec.Codec;
import limax.codec.CodecException;
import limax.codec.SinkStream;

public class ASN1PrimitiveContainer extends ASN1PrimitiveObject implements ConstructedObject {
	public ASN1PrimitiveContainer(ASN1Tag tag) {
		super(tag);
	}

	public ASN1PrimitiveContainer(ASN1Tag tag, byte[] data) {
		super(tag, data);
	}

	@Override
	public void render(Codec c) throws CodecException {
		getTag().render(c, false);
		byte[] data = getData();
		byte[] blen = BERLength.render(data.length);
		c.update(blen, 0, blen.length);
		c.update(data, 0, data.length);
	}

	@Override
	public String toString() {
		return String.format("ASN1PrimitiveContainer [%s, %d]", getTag(), getData().length);
	}

	@Override
	public void addChildren(Collection<ASN1Object> objects) throws CodecException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (ASN1Object obj : objects)
			obj.render(new SinkStream(out));
		setData(out.toByteArray());
	}

}
