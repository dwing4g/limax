package limax.switcher;

import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;

public final class LmkInfo {
	private final String uid;
	private final Octets lmkdata;

	LmkInfo(String uid, Octets lmkData) {
		this.uid = uid;
		this.lmkdata = lmkData;
	}

	public LmkInfo(String uid) {
		this(uid, new Octets());
	}

	public LmkInfo(Octets binary) throws MarshalException {
		OctetsStream os = OctetsStream.wrap(binary);
		this.uid = os.unmarshal_String();
		this.lmkdata = os.unmarshal_Octets();
	}

	public Octets encode() {
		return new OctetsStream().marshal(uid).marshal(lmkdata);
	}

	public String getUid() {
		return uid;
	}

	public Octets getLmkData() {
		return lmkdata;
	}
}
