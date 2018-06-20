package limax.codec.asn1;

import java.util.Collection;

class BERObjectHuge extends BERObject {
	private final HugeObject navigateObject;

	public BERObjectHuge(HugeObject navigateObject) {
		this.navigateObject = navigateObject;
	}

	@Override
	public DecodeBER getReady() {
		setObjectBuilder(navigateObject);
		return isConstructed() ? new DecodeBER(new BERObjectHuge(navigateObject)) : null;
	}

	@Override
	public void update(Collection<ASN1Object> objects) {
	}

	@Override
	public ASN1Object endorse() {
		return null;
	}
}
