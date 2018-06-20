package limax.zdb.tool;

import limax.codec.MarshalException;
import limax.codec.OctetsStream;

public class DataInt implements Data {
	private final IntType type;
	private long value;

	DataInt(SchemaInt schema) {
		type = schema.intType();
	}

	public long getValue() {
		return value;
	}

	public IntType getIntType() {
		return type;
	}

	public void setValue(long value) {
		this.value = value;
	}

	@Override
	public void convertTo(Data t) {
		if (t instanceof DataFloat) { // maybe_auto
			((DataFloat) t).setValue(value);
		} else {
			((DataInt) t).value = value;
		}
	}

	@Override
	public OctetsStream marshal(OctetsStream os) {
		switch (type) {
		case BOOLEAN:
		case BYTE:
			return os.marshal((byte) value);
		case SHORT:
			return os.marshal((short) value);
		case INT:
			return os.marshal((int) value);
		default:
			return os.marshal(value);
		}
	}

	@Override
	public OctetsStream unmarshal(OctetsStream os) throws MarshalException {
		switch (type) {
		case BOOLEAN:
		case BYTE:
			value = os.unmarshal_byte();
			break;
		case SHORT:
			value = os.unmarshal_short();
			break;
		case INT:
			value = os.unmarshal_int();
			break;
		default:
			value = os.unmarshal_long();
			break;
		}
		return os;
	}

	@Override
	public String toString() {
		switch (type) {
		case BOOLEAN:
			return String.valueOf(value != 0);
		case BYTE:
			return String.valueOf((byte) value);
		case SHORT:
			return String.valueOf((short) value);
		case INT:
			return String.valueOf((int) value);
		default:
			return String.valueOf(value);
		}
	}

}
