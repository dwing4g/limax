package limax.codec.asn1;

import java.util.List;

interface ContainerObject extends ConstructedObject {
	void setLength(long length);

	int size();

	ASN1Object get(int index);

	List<ASN1Object> getChildren();

	void set(int index, ASN1Object object);

	void addChild(ASN1Object object);

	void addChildren(ASN1Object... objects);
}
