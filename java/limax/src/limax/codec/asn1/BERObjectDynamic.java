package limax.codec.asn1;

class BERObjectDynamic extends BERObjectNavigable {
	private final ASN1Object navigateObject;

	public BERObjectDynamic(ASN1Object dynamicObject) {
		this.navigateObject = dynamicObject;
	}

	@Override
	public DecodeBER getReady() {
		ASN1Object navObj = ((DynamicObject) navigateObject).create(getTag());
		if (navObj instanceof ASN1Optional)
			throw new BERException("Dynamic decode process MUST NOT submit ASN1Optional Object");
		return objectDecoder(navObj);
	}

}
