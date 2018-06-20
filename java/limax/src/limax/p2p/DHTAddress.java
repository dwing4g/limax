package limax.p2p;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import limax.codec.Marshal;
import limax.codec.MarshalException;
import limax.codec.OctetsStream;
import limax.util.Helper;

public class DHTAddress implements Marshal {
	private static final String HASH = System.getProperty("limax.p2p.DHTAddress.HASH", "SHA-256");
	private BigInteger metric;

	public DHTAddress() {
		this(Helper.makeRandValues(128));
	}

	public DHTAddress(byte[] source) {
		try {
			MessageDigest md = MessageDigest.getInstance(HASH);
			metric = new BigInteger(1, md.digest(source));
		} catch (NoSuchAlgorithmException e) {
		}
	}

	public DHTAddress(ByteBuffer source) {
		try {
			MessageDigest md = MessageDigest.getInstance(HASH);
			md.update(source);
			metric = new BigInteger(1, md.digest());
		} catch (NoSuchAlgorithmException e) {
		}
	}

	public DHTAddress(OctetsStream os) throws MarshalException {
		unmarshal(os);
	}

	public BigInteger distance(DHTAddress other) {
		return metric.xor(other.metric);
	}

	@Override
	public OctetsStream marshal(OctetsStream os) {
		return os.marshal(metric.toByteArray());
	}

	@Override
	public OctetsStream unmarshal(OctetsStream os) throws MarshalException {
		metric = new BigInteger(os.unmarshal_bytes());
		return os;
	}

	@Override
	public int hashCode() {
		return metric.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		return obj instanceof DHTAddress ? metric.equals(((DHTAddress) obj).metric) : false;
	}

	@Override
	public String toString() {
		return metric.toString(16);
	}
}
