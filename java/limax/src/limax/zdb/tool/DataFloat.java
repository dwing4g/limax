package limax.zdb.tool;

import limax.codec.MarshalException;
import limax.codec.OctetsStream;

public class DataFloat implements Data {
	private boolean isDouble;
	private double value;

	DataFloat(SchemaFloat schema) {
		this.isDouble = schema.isDouble();
	}

	public double getValue() {
		return value;
	}

	public boolean isDouble() {
		return isDouble;
	}

	public void setValue(double value) {
		this.value = value;
	}

	@Override
	public void convertTo(Data t) {
		((DataFloat) t).value = value;
	}

	@Override
	public OctetsStream marshal(OctetsStream os) {
		if (isDouble)
			return os.marshal(value);
		else
			return os.marshal((float) value);
	}

	@Override
	public OctetsStream unmarshal(OctetsStream os) throws MarshalException {
		if (isDouble)
			value = os.unmarshal_double();
		else
			value = os.unmarshal_float();
		return os;
	}

	@Override
	public String toString() {
		return isDouble ? String.valueOf(value) : String.valueOf((float) value);
	}

}
