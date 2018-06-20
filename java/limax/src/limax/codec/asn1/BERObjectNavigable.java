package limax.codec.asn1;

import java.util.Collection;

import limax.codec.CodecException;

abstract class BERObjectNavigable extends BERObject {
	private ASN1Object navigateObject;

	protected DecodeBER objectDecoder(ASN1Object obj) {
		if (obj instanceof ASN1Choice)
			obj = ((ASN1Choice) obj).choose(getTag());
		navigateObject = obj;
		if (obj instanceof ASN1Any) {
			((ASN1Any) navigateObject).set(obj = tag2Object(getTag()));
			return wrapDecoder(obj);
		} else if (obj instanceof HugeObject) {
			setObjectBuilder((HugeObject) obj);
			return isConstructed() ? new DecodeBER(new BERObjectHuge((HugeObject) obj)) : null;
		} else {
			if (isConstructed()) {
				if (obj instanceof ContainerObject) {
					setObjectBuilder(new ConstructedBuilder((ConstructedObject) obj, getLength()));
					return new DecodeBER(
							obj instanceof DynamicObject ? new BERObjectDynamic(obj) : new BERObjectStatic(obj));
				} else if (obj instanceof ASN1PrimitiveContainer) {
					if (getLength() == -1) {
						setObjectBuilder(new ConstructedBuilder((ConstructedObject) obj, -1));
						return new DecodeBER(new BERObjectPassThrough());
					} else {
						setObjectBuilder(new PrimitiveBuilder((PrimitiveObject) obj));
						return null;
					}
				} else {
					setObjectBuilder(new ConstructedBuilder((ConstructedObject) obj, getLength()));
					return new DecodeBER(new BERObject());
				}
			} else {
				setObjectBuilder(new PrimitiveBuilder((PrimitiveObject) obj));
				return null;
			}
		}
	}

	@Override
	public void update(Collection<ASN1Object> objects) throws CodecException {
		if (navigateObject instanceof ASN1Any || navigateObject instanceof ASN1PrimitiveContainer
				|| (navigateObject instanceof ConstructedObject && !(navigateObject instanceof ContainerObject))) {
			super.update(objects);
		} else if (navigateObject instanceof DynamicObject) {
			((DynamicObject) navigateObject).addChildren(objects);
		}
	}

}
