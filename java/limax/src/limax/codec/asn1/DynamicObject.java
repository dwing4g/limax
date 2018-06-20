package limax.codec.asn1;

public interface DynamicObject extends ContainerObject {
	ASN1Object create(ASN1Tag tag);
}
