package limax.key;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;

import limax.codec.Marshal;
import limax.codec.MarshalException;
import limax.codec.OctetsStream;
import limax.p2p.DHTAddress;

class SearchRequest implements Marshal {
	private DHTAddress searchFor;
	private DHTAddress localDHTAddress;
	private InetAddress localInetAddress;
	private Collection<Long> timestamps;

	SearchRequest(DHTAddress searchFor, DHTAddress localDHTAddress, InetAddress localInetAddress,
			Collection<Long> timestamps) {
		this.searchFor = searchFor;
		this.localDHTAddress = localDHTAddress;
		this.localInetAddress = localInetAddress;
		this.timestamps = timestamps;
	}

	SearchRequest(OctetsStream os) throws MarshalException {
		unmarshal(os);
	}

	DHTAddress getSearchFor() {
		return searchFor;
	}

	DHTAddress getLocalDHTAddress() {
		return localDHTAddress;
	}

	InetAddress getLocalInetAddress() {
		return localInetAddress;
	}

	Collection<Long> getTimestamps() {
		return timestamps;
	}

	@Override
	public OctetsStream marshal(OctetsStream os) {
		os.marshal(searchFor);
		os.marshal(localDHTAddress);
		os.marshal(localInetAddress.getAddress());
		os.marshal_size(timestamps.size());
		timestamps.forEach(timestamp -> os.marshal(timestamp));
		return os;
	}

	@Override
	public OctetsStream unmarshal(OctetsStream os) throws MarshalException {
		this.searchFor = new DHTAddress(os);
		this.localDHTAddress = new DHTAddress(os);
		try {
			this.localInetAddress = InetAddress.getByAddress(os.unmarshal_bytes());
		} catch (UnknownHostException e) {
		}
		this.timestamps = new ArrayList<>();
		for (int i = 0, size = os.unmarshal_size(); i < size; i++)
			this.timestamps.add(os.unmarshal_long());
		return os;
	}
}
