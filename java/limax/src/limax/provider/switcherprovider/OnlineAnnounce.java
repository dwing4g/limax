
package limax.provider.switcherprovider;

import limax.provider.__ProtocolProcessManager;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class OnlineAnnounce extends limax.net.Protocol {
	@Override
	public void process() {
		__ProtocolProcessManager.process(this);
	}

	// {{{ XMLGEN_DEFINE_BEGIN
	// {{{ DO NOT EDIT THIS
	public static int TYPE;

	public int getType() {
		return TYPE;
	}

    public long sessionid; 
    public long mainid; 
    public String uid; 
    public limax.codec.Octets clientaddress; 
    public long flags; 
    public java.util.HashMap<Integer, Byte> sessiontype; 
    public limax.defines.ProviderLoginData logindata; 

	public OnlineAnnounce() {
		uid = "";
		clientaddress = new limax.codec.Octets();
		sessiontype = new java.util.HashMap<Integer, Byte>();
		logindata = new limax.defines.ProviderLoginData();
	}

	public OnlineAnnounce(long _sessionid_, long _mainid_, String _uid_, limax.codec.Octets _clientaddress_, long _flags_, java.util.HashMap<Integer, Byte> _sessiontype_, limax.defines.ProviderLoginData _logindata_) {
		this.sessionid = _sessionid_;
		this.mainid = _mainid_;
		this.uid = _uid_;
		this.clientaddress = _clientaddress_;
		this.flags = _flags_;
		this.sessiontype = _sessiontype_;
		this.logindata = _logindata_;
	}

	@Override
	public limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {
		_os_.marshal(this.sessionid);
		_os_.marshal(this.mainid);
		_os_.marshal(this.uid);
		_os_.marshal(this.clientaddress);
		_os_.marshal(this.flags);
		_os_.marshal_size(this.sessiontype.size());
		for (java.util.Map.Entry<Integer, Byte> _e_ : this.sessiontype.entrySet()) {
			_os_.marshal(_e_.getKey());
			_os_.marshal(_e_.getValue());
		}
		_os_.marshal(this.logindata);
		return _os_;
	}

	@Override
	public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {
		this.sessionid = _os_.unmarshal_long();
		this.mainid = _os_.unmarshal_long();
		this.uid = _os_.unmarshal_String();
		this.clientaddress = _os_.unmarshal_Octets();
		this.flags = _os_.unmarshal_long();
		for(int _i_ = _os_.unmarshal_size(); _i_ > 0; --_i_) {
			int _k_ = _os_.unmarshal_int();
			byte _v_ = _os_.unmarshal_byte();
			this.sessiontype.put(_k_, _v_);
		}
		this.logindata.unmarshal(_os_);
		return _os_;
	}

	@Override
	public String toString() {
		StringBuilder _sb_ = new StringBuilder(super.toString());
		_sb_.append("=(");
		_sb_.append(this.sessionid).append(",");
		_sb_.append(this.mainid).append(",");
		_sb_.append("T").append(this.uid.length()).append(",");
		_sb_.append("B").append(this.clientaddress.size()).append(",");
		_sb_.append(this.flags).append(",");
		_sb_.append(this.sessiontype).append(",");
		_sb_.append(this.logindata).append(",");
		_sb_.append(")");
		return _sb_.toString();
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}

}
