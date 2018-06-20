package limax.codec.asn1;

class BERObjectStatic extends BERObjectNavigable {
	private final ContainerObject navigateObject;
	private int index = 0;

	public BERObjectStatic(ASN1Object navigateObject) {
		this.navigateObject = (ContainerObject) navigateObject;
	}

	@Override
	public DecodeBER getReady() {
		ASN1Tag tag = getTag();
		ASN1Object navObj = navigateObject.get(index++);
		while (!navObj.compareTag(tag) && navObj instanceof ASN1Optional)
			navObj = navigateObject.get(index++);
		if (!navObj.compareTag(tag))
			throw new BERException("navigate " + navObj.getTag() + " but " + tag);
		return objectDecoder(navObj instanceof ASN1Optional ? ((ASN1Optional) navObj).get() : navObj);
	}

}
