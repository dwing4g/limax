package limax.zdb.tool;

import limax.codec.MarshalException;
import limax.codec.OctetsStream;

public class DataBinary implements Data {
	private byte[] value = new byte[0];

	DataBinary() {
	}

	public byte[] getValue() {
		return value;
	}

	public void setValue(byte[] value) {
		this.value = value;
	}

	@Override
	public void convertTo(Data t) {
		((DataBinary) t).value = value;
	}

	@Override
	public OctetsStream marshal(OctetsStream os) {
		return os.marshal(value);
	}

	@Override
	public OctetsStream unmarshal(OctetsStream os) throws MarshalException {
		value = os.unmarshal_bytes();
		return os;
	}

	@Override
	public String toString() {
		return "B" + value.length;
	}
}
