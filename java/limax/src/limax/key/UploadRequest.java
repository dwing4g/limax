package limax.key;

import java.util.ArrayList;
import java.util.Collection;

import limax.codec.Marshal;
import limax.codec.MarshalException;
import limax.codec.OctetsStream;
import limax.util.Pair;

class UploadRequest implements Marshal {
	private Collection<Pair<Long, byte[]>> keyPairs;

	UploadRequest(Collection<Pair<Long, byte[]>> keyPairs) {
		this.keyPairs = keyPairs;
	}

	UploadRequest(OctetsStream os) throws MarshalException {
		unmarshal(os);
	}

	Collection<Pair<Long, byte[]>> getKeyPairs() {
		return keyPairs;
	}

	@Override
	public OctetsStream marshal(OctetsStream os) {
		os.marshal_size(keyPairs.size());
		keyPairs.forEach(pair -> os.marshal(pair.getKey()).marshal(pair.getValue()));
		return os;
	}

	@Override
	public OctetsStream unmarshal(OctetsStream os) throws MarshalException {
		this.keyPairs = new ArrayList<>();
		for (int i = 0, size = os.unmarshal_size(); i < size; i++)
			this.keyPairs.add(new Pair<>(os.unmarshal_long(), os.unmarshal_bytes()));
		return os;
	}

}
