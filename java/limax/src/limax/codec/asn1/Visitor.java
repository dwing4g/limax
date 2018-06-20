package limax.codec.asn1;

public interface Visitor {
	void update(ASN1Object obj);

	void enter();

	void leave();
}
