package limax.zdb.tool;

import limax.codec.MarshalException;
import limax.codec.OctetsStream;
import limax.util.StringUtils;

public class DataString implements Data {
	private String value = "";

	DataString() {
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	@Override
	public void convertTo(Data t) {
		((DataString) t).value = value;
	}

	@Override
	public OctetsStream marshal(OctetsStream os) {
		return os.marshal(value);
	}

	@Override
	public OctetsStream unmarshal(OctetsStream os) throws MarshalException {
		value = os.unmarshal_String();
		return os;
	}

	@Override
	public String toString() {
		return StringUtils.quote(value);
	}
}
