package limax.codec;

public interface JSONMarshal extends JSONSerializable {
	JSONBuilder marshal(JSONBuilder jb);
}
