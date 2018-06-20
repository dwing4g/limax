package limax.key;

import java.util.ArrayList;
import java.util.Collection;

import limax.codec.Marshal;
import limax.codec.MarshalException;
import limax.codec.OctetsStream;
import limax.p2p.DHTAddress;
import limax.p2p.NetworkID;
import limax.util.Pair;

class SearchResponse implements Marshal {
	private DHTAddress localDHTAddress;
	private Collection<NetworkID> candidates;
	private Collection<Pair<Long, byte[]>> keyPairs;
	private Collection<Long> timestamps;

	SearchResponse(DHTAddress localDHTAddress, Collection<NetworkID> candidates,
			Collection<Pair<Long, byte[]>> keyPairs, Collection<Long> timestamps) {
		this.localDHTAddress = localDHTAddress;
		this.candidates = candidates;
		this.keyPairs = keyPairs;
		this.timestamps = timestamps;
	}

	SearchResponse(OctetsStream os) throws MarshalException {
		unmarshal(os);
	}

	DHTAddress getLocalDHTAddress() {
		return localDHTAddress;
	}

	Collection<NetworkID> getCandidates() {
		return candidates;
	}

	Collection<Pair<Long, byte[]>> getKeyPairs() {
		return keyPairs;
	}

	Collection<Long> getTimestamps() {
		return timestamps;
	}

	@Override
	public OctetsStream marshal(OctetsStream os) {
		os.marshal(localDHTAddress);
		os.marshal_size(candidates.size());
		candidates.forEach(nid -> os.marshal(nid));
		os.marshal_size(keyPairs.size());
		keyPairs.forEach(pair -> os.marshal(pair.getKey()).marshal(pair.getValue()));
		os.marshal_size(timestamps.size());
		timestamps.forEach(timestamp -> os.marshal(timestamp));
		return os;
	}

	@Override
	public OctetsStream unmarshal(OctetsStream os) throws MarshalException {
		this.localDHTAddress = new DHTAddress(os);
		this.candidates = new ArrayList<>();
		for (int i = 0, size = os.unmarshal_size(); i < size; i++)
			this.candidates.add(new NetworkID(os));
		this.keyPairs = new ArrayList<>();
		for (int i = 0, size = os.unmarshal_size(); i < size; i++)
			this.keyPairs.add(new Pair<>(os.unmarshal_long(), os.unmarshal_bytes()));
		this.timestamps = new ArrayList<>();
		for (int i = 0, size = os.unmarshal_size(); i < size; i++)
			this.timestamps.add(os.unmarshal_long());
		return os;
	}

}
