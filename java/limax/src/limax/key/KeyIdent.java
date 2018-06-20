package limax.key;

import java.util.Objects;

import limax.codec.Marshal;
import limax.codec.MarshalException;
import limax.codec.OctetsStream;

class KeyIdent implements Marshal {
	private long timestamp;
	private long nonce;
	private String group;

	KeyIdent(long timestamp, long nonce, String group) {
		this.timestamp = timestamp;
		this.nonce = nonce;
		this.group = group;
	}

	KeyIdent(OctetsStream os) throws MarshalException {
		unmarshal(os);
	}

	long getTimestamp() {
		return timestamp;
	}

	void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	long getNonce() {
		return nonce;
	}

	String getGroup() {
		return group;
	}

	@Override
	public OctetsStream marshal(OctetsStream os) {
		return os.marshal(timestamp).marshal(nonce).marshal(group);
	}

	@Override
	public OctetsStream unmarshal(OctetsStream os) throws MarshalException {
		this.timestamp = os.unmarshal_long();
		this.nonce = os.unmarshal_long();
		this.group = os.unmarshal_String();
		return os;
	}

	@Override
	public int hashCode() {
		return Objects.hash(timestamp, nonce, group);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (obj instanceof KeyIdent) {
			KeyIdent o = (KeyIdent) obj;
			return o.timestamp == timestamp && o.nonce == nonce && o.group.equals(group);
		}
		return false;
	}

	@Override
	public String toString() {
		return timestamp + " " + nonce + " " + group;
	}
}
