package limax.codec.asn1;

import java.util.Collection;

import limax.codec.CodecException;

class ConstructedBuilder implements ObjectBuilder, ConstructedInput {
	private final ConstructedObject obj;

	public ConstructedBuilder(ConstructedObject obj, long length) {
		this.obj = obj;
		if (obj instanceof ContainerObject)
			((ContainerObject) obj).setLength(length);
	}

	@Override
	public void update(Collection<ASN1Object> objects) throws CodecException {
		obj.addChildren(objects);
	}

	@Override
	public ASN1Object endorse() {
		return (ASN1Object) obj;
	}
}
