package limax.codec.asn1;

class BERObjectRoot extends BERObjectNavigable {
	private final ASN1Object navigateObject;

	public BERObjectRoot(ASN1Object navigateObject) {
		this.navigateObject = navigateObject instanceof ASN1Optional ? ((ASN1Optional) navigateObject).get()
				: navigateObject;
	}

	@Override
	public DecodeBER getReady() {
		if (!navigateObject.compareTag(getTag()))
			throw new BERException("navigate " + navigateObject.getTag() + " but " + getTag());
		return objectDecoder(navigateObject);
	}
}
