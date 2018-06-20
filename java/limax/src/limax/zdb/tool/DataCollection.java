package limax.zdb.tool;

import java.util.ArrayList;
import java.util.Collection;

import limax.codec.MarshalException;
import limax.codec.OctetsStream;

public class DataCollection implements Data {
	private final Schema elementSchema;
	private final Collection<Data> value = new ArrayList<>();

	DataCollection(SchemaCollection schema) {
		elementSchema = schema.elementSchema();
	}

	public Collection<Data> getValue() {
		return value;
	}

	@Override
	public void convertTo(Data t) {
		DataCollection target = (DataCollection) t;
		for (Data data : value) {
			Data targetData = target.elementSchema.create();
			data.convertTo(targetData);
			target.value.add(targetData);
		}
	}

	@Override
	public OctetsStream marshal(OctetsStream os) {
		os.marshal_size(value.size());
		value.forEach(data -> data.marshal(os));
		return os;
	}

	@Override
	public OctetsStream unmarshal(OctetsStream os) throws MarshalException {
		for (int size = os.unmarshal_size(); size > 0; size--) {
			Data data = elementSchema.create();
			data.unmarshal(os);
			value.add(data);
		}
		return os;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("[");
		value.forEach(data -> sb.append(data).append(", "));
		return sb.append("]").toString();
	}
}
