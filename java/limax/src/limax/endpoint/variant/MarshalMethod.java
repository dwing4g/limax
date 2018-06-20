package limax.endpoint.variant;

import limax.codec.MarshalException;
import limax.codec.OctetsStream;

public interface MarshalMethod {
	OctetsStream marshal(OctetsStream os, Variant v);

	Variant unmarshal(OctetsStream os) throws MarshalException;

	Declaration getDeclaration();
}
