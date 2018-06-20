package limax.p2p;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import limax.codec.Marshal;
import limax.codec.MarshalException;
import limax.codec.OctetsStream;

public class NetworkID implements Marshal {
	private DHTAddress dhtAddress;
	private InetSocketAddress inetSocketAddress;
	private BigInteger secondaryKey;

	private void computeSecondaryKey() {
		secondaryKey = new BigInteger(1, inetSocketAddress.getAddress().getAddress()).shiftLeft(32)
				.or(BigInteger.valueOf(inetSocketAddress.getPort()));
	}

	BigInteger getSecondaryKey() {
		return secondaryKey;
	}

	public NetworkID(DHTAddress dhtAddress, InetSocketAddress inetSocketAddress) {
		this.dhtAddress = dhtAddress;
		this.inetSocketAddress = inetSocketAddress;
		computeSecondaryKey();
	}

	public NetworkID(OctetsStream os) throws MarshalException {
		unmarshal(os);
		computeSecondaryKey();
	}

	public DHTAddress getDHTAddress() {
		return dhtAddress;
	}

	public InetSocketAddress getInetSocketAddress() {
		return inetSocketAddress;
	}

	@Override
	public String toString() {
		return dhtAddress.toString() + ":" + inetSocketAddress;
	}

	@Override
	public int hashCode() {
		return dhtAddress.hashCode() ^ inetSocketAddress.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (obj instanceof NetworkID) {
			NetworkID o = (NetworkID) obj;
			return dhtAddress.equals(o.dhtAddress) && inetSocketAddress.equals(o.inetSocketAddress);
		}
		return false;
	}

	@Override
	public OctetsStream marshal(OctetsStream os) {
		return os.marshal(dhtAddress).marshal(inetSocketAddress.getAddress().getAddress())
				.marshal(inetSocketAddress.getPort());
	}

	@Override
	public OctetsStream unmarshal(OctetsStream os) throws MarshalException {
		this.dhtAddress = new DHTAddress(os);
		try {
			this.inetSocketAddress = new InetSocketAddress(InetAddress.getByAddress(os.unmarshal_bytes()),
					os.unmarshal_int());
		} catch (UnknownHostException e) {
		}
		return os;
	}
}
