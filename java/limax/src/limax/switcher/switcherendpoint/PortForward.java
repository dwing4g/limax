
package limax.switcher.switcherendpoint;

import limax.switcher.SwitcherListener;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class PortForward extends limax.net.Protocol {
	@Override
	public void process() throws Exception {
		SwitcherListener.getInstance().process(this);
	}

	// {{{ XMLGEN_DEFINE_BEGIN
	// {{{ DO NOT EDIT THIS
	public static int TYPE;

	public int getType() {
		return TYPE;
	}

    public static final int eConnect = 1; 
    public static final int eClose = 2; 
    public static final int eForward = 3; 
    public static final int eForwardAck = 4; 
    public static final int eAuthority = 5; 
    public static final int eConnectV0 = 0; 
    public static final int eCloseUnknownConnectVersion = 1; 
    public static final int eCloseUnknownForwardType = 2; 
    public static final int eCloseForwardPortNotFound = 3; 
    public static final int eCloseConnectDuplicatePort = 4; 
    public static final int eCloseSessionAbort = 5; 
    public static final int eCloseSessionClose = 6; 
    public static final int eCloseForwardAckPortNotFound = 7; 
    public static final int eCloseManualClosed = 8; 
    public static final int eCloseNoAuthority = 9; 
    public static final int eForwardRaw = 0; 

    public int command; 
    public int portsid; 
    public int code; 
    public limax.codec.Octets data; 

	public PortForward() {
		data = new limax.codec.Octets();
	}

	public PortForward(int _command_, int _portsid_, int _code_, limax.codec.Octets _data_) {
		this.command = _command_;
		this.portsid = _portsid_;
		this.code = _code_;
		this.data = _data_;
	}

	@Override
	public limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {
		_os_.marshal(this.command);
		_os_.marshal(this.portsid);
		_os_.marshal(this.code);
		_os_.marshal(this.data);
		return _os_;
	}

	@Override
	public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {
		this.command = _os_.unmarshal_int();
		this.portsid = _os_.unmarshal_int();
		this.code = _os_.unmarshal_int();
		this.data = _os_.unmarshal_Octets();
		return _os_;
	}

	@Override
	public String toString() {
		StringBuilder _sb_ = new StringBuilder(super.toString());
		_sb_.append("=(");
		_sb_.append(this.command).append(",");
		_sb_.append(this.portsid).append(",");
		_sb_.append(this.code).append(",");
		_sb_.append("B").append(this.data.size()).append(",");
		_sb_.append(")");
		return _sb_.toString();
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}

}
