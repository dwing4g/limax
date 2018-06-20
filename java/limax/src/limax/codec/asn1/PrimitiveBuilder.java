package limax.codec.asn1;

import java.io.ByteArrayOutputStream;

class PrimitiveBuilder implements ObjectBuilder, PrimitiveInput {
	private final PrimitiveObject obj;
	private final ByteArrayOutputStream out = new ByteArrayOutputStream();

	public PrimitiveBuilder(PrimitiveObject obj) {
		this.obj = obj;
	}

	@Override
	public void update(byte[] data, int off, int len) {
		out.write(data, off, len);
	}

	@Override
	public ASN1Object endorse() {
		obj.setData(out.toByteArray());
		return (ASN1Object) obj;
	}
}
