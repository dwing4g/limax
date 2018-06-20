package limax.codec.asn1;

class BERObjectPassThrough extends BERObject {
	@Override
	public DecodeBER getReady() {
		if (isConstructed()) {
			if (getLength() == -1) {
				setObjectBuilder(new ConstructedBuilder(new ASN1PrimitiveContainer(getTag()), -1));
				return new DecodeBER(new BERObjectPassThrough());
			} else {
				setObjectBuilder(new PrimitiveBuilder(new ASN1PrimitiveContainer(getTag())));
				return null;
			}
		} else {
			setObjectBuilder(new PrimitiveBuilder(new ASN1PrimitiveObject(getTag())));
			return null;
		}
	}
}
