
package limax.endpoint.switcherendpoint;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class SessionLoginByToken extends limax.net.Protocol {
	@Override
	public void process() {
		// protocol handle
	}

	// {{{ XMLGEN_DEFINE_BEGIN
	// {{{ DO NOT EDIT THIS
	public static int TYPE;

	public int getType() {
		return TYPE;
	}

    public String username; 
    public String token; 
    public String platflag; 
    public java.util.HashMap<Integer, Byte> pvids; 
    public limax.codec.Octets report_ip; 
    public short report_port; 

	public SessionLoginByToken() {
		username = "";
		token = "";
		platflag = "";
		pvids = new java.util.HashMap<Integer, Byte>();
		report_ip = new limax.codec.Octets();
	}

	public SessionLoginByToken(String _username_, String _token_, String _platflag_, java.util.HashMap<Integer, Byte> _pvids_, limax.codec.Octets _report_ip_, short _report_port_) {
		this.username = _username_;
		this.token = _token_;
		this.platflag = _platflag_;
		this.pvids = _pvids_;
		this.report_ip = _report_ip_;
		this.report_port = _report_port_;
	}

	@Override
	public limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {
		_os_.marshal(this.username);
		_os_.marshal(this.token);
		_os_.marshal(this.platflag);
		_os_.marshal_size(this.pvids.size());
		for (java.util.Map.Entry<Integer, Byte> _e_ : this.pvids.entrySet()) {
			_os_.marshal(_e_.getKey());
			_os_.marshal(_e_.getValue());
		}
		_os_.marshal(this.report_ip);
		_os_.marshal(this.report_port);
		return _os_;
	}

	@Override
	public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {
		this.username = _os_.unmarshal_String();
		this.token = _os_.unmarshal_String();
		this.platflag = _os_.unmarshal_String();
		for(int _i_ = _os_.unmarshal_size(); _i_ > 0; --_i_) {
			int _k_ = _os_.unmarshal_int();
			byte _v_ = _os_.unmarshal_byte();
			this.pvids.put(_k_, _v_);
		}
		this.report_ip = _os_.unmarshal_Octets();
		this.report_port = _os_.unmarshal_short();
		return _os_;
	}

	@Override
	public String toString() {
		StringBuilder _sb_ = new StringBuilder(super.toString());
		_sb_.append("=(");
		_sb_.append("T").append(this.username.length()).append(",");
		_sb_.append("T").append(this.token.length()).append(",");
		_sb_.append("T").append(this.platflag.length()).append(",");
		_sb_.append(this.pvids).append(",");
		_sb_.append("B").append(this.report_ip.size()).append(",");
		_sb_.append(this.report_port).append(",");
		_sb_.append(")");
		return _sb_.toString();
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}

}
