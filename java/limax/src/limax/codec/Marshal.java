package limax.codec;

public interface Marshal {
	OctetsStream marshal(OctetsStream os);

	OctetsStream unmarshal(OctetsStream os) throws MarshalException;
}
