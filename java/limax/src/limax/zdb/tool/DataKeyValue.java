package limax.zdb.tool;

import limax.codec.MarshalException;
import limax.codec.OctetsStream;
import limax.zdb.XBean;

public class DataKeyValue implements Data {
	private final Data key;
	private final Data value;
	private XBean.DynamicData dynamic;

	DataKeyValue(SchemaKeyValue schema) {
		key = schema.keySchema().create();
		value = schema.valueSchema().create();
		if (schema.valueSchema().isDynamic())
			dynamic = new XBean.DynamicData();
	}

	public Data getKey() {
		return key;
	}

	public Data getValue() {
		return value;
	}

	public XBean.DynamicData getDynamicData() {
		return dynamic;
	}

	@Override
	public void convertTo(Data t) {
		DataKeyValue target = (DataKeyValue) t;
		key.convertTo(target.key);
		value.convertTo(target.value);
		target.dynamic = dynamic;
	}

	@Override
	public OctetsStream marshal(OctetsStream os) {
		os.marshal(key).marshal(value);
		if (dynamic != null)
			dynamic.marshal(os);
		return os;
	}

	@Override
	public OctetsStream unmarshal(OctetsStream os) throws MarshalException {
		os.unmarshal(key).unmarshal(value);
		if (dynamic != null)
			dynamic.unmarshal(os);
		return os;
	}

	@Override
	public String toString() {
		return key + ": " + value + (dynamic != null ? dynamic.serials() : "");
	}
}
