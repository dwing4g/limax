package limax.zdb.tool;

import java.util.LinkedHashMap;
import java.util.Map;

import limax.codec.MarshalException;
import limax.codec.OctetsStream;

public class DataBean implements Data {
	private final Map<String, Data> entries = new LinkedHashMap<>();

	DataBean(SchemaBean schema) {
		schema.entries().forEach((k, v) -> entries.put(k, v.create()));
	}

	public Map<String, Data> entries() {
		return entries;
	}

	@Override
	public void convertTo(Data t) {
		DataBean target = (DataBean) t;
		target.entries.forEach((name, targetData) -> {
			Data data = entries.get(name);
			if (data != null)
				data.convertTo(targetData);
		});
	}

	@Override
	public OctetsStream marshal(OctetsStream os) {
		entries.values().forEach(data -> data.marshal(os));
		return os;
	}

	@Override
	public OctetsStream unmarshal(OctetsStream os) throws MarshalException {
		for (Data data : entries.values())
			data.unmarshal(os);
		return os;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("{");
		entries.forEach((name, data) -> sb.append(name).append(":").append(data).append(", "));
		return sb.append("}").toString();
	}
}
