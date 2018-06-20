package limax.key;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;

import limax.codec.Marshal;
import limax.codec.MarshalException;
import limax.codec.OctetsStream;

class KeyResponse implements Marshal {
	private long timestamp;
	private byte[] key;
	private Collection<InetAddress> randomServers;

	KeyResponse(long timestamp, byte[] key, Collection<InetAddress> randomServers) {
		this.timestamp = timestamp;
		this.key = key;
		this.randomServers = randomServers;
	}

	KeyResponse(OctetsStream os) throws MarshalException {
		unmarshal(os);
	}

	long getTimestamp() {
		return timestamp;
	}

	byte[] getKey() {
		return key;
	}

	Collection<InetAddress> getRandomServers() {
		return randomServers;
	}

	@Override
	public OctetsStream marshal(OctetsStream os) {
		os.marshal(timestamp).marshal(key);
		os.marshal_size(randomServers.size());
		randomServers.forEach(inetAddress -> os.marshal(inetAddress.getAddress()));
		return os;
	}

	@Override
	public OctetsStream unmarshal(OctetsStream os) throws MarshalException {
		this.timestamp = os.unmarshal_long();
		this.key = os.unmarshal_bytes();
		this.randomServers = new ArrayList<>();
		for (int i = 0, size = os.unmarshal_size(); i < size; i++)
			try {
				this.randomServers.add(InetAddress.getByAddress(os.unmarshal_bytes()));
			} catch (UnknownHostException e) {
			}
		return os;
	}

}
