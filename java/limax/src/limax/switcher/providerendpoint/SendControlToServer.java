
package limax.switcher.providerendpoint;

import limax.switcher.SwitcherListener;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class SendControlToServer extends limax.net.Protocol {
	@Override
	public void process() {
		SwitcherListener.getInstance().process(this);
	}

	// {{{ XMLGEN_DEFINE_BEGIN
	// {{{ DO NOT EDIT THIS
	public static int TYPE;

	public int getType() {
		return TYPE;
	}

    public int providerid; 
    public long sessionid; 
    public short classindex; 
    public int instanceindex; 
    public byte controlindex; 
    public limax.codec.Octets controlparameter; 
    public String stringdata; 

	public SendControlToServer() {
		controlparameter = new limax.codec.Octets();
		stringdata = "";
	}

	public SendControlToServer(int _providerid_, long _sessionid_, short _classindex_, int _instanceindex_, byte _controlindex_, limax.codec.Octets _controlparameter_, String _stringdata_) {
		this.providerid = _providerid_;
		this.sessionid = _sessionid_;
		this.classindex = _classindex_;
		this.instanceindex = _instanceindex_;
		this.controlindex = _controlindex_;
		this.controlparameter = _controlparameter_;
		this.stringdata = _stringdata_;
	}

	@Override
	public limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {
		_os_.marshal(this.providerid);
		_os_.marshal(this.sessionid);
		_os_.marshal(this.classindex);
		_os_.marshal(this.instanceindex);
		_os_.marshal(this.controlindex);
		_os_.marshal(this.controlparameter);
		_os_.marshal(this.stringdata);
		return _os_;
	}

	@Override
	public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {
		this.providerid = _os_.unmarshal_int();
		this.sessionid = _os_.unmarshal_long();
		this.classindex = _os_.unmarshal_short();
		this.instanceindex = _os_.unmarshal_int();
		this.controlindex = _os_.unmarshal_byte();
		this.controlparameter = _os_.unmarshal_Octets();
		this.stringdata = _os_.unmarshal_String();
		return _os_;
	}

	@Override
	public String toString() {
		StringBuilder _sb_ = new StringBuilder(super.toString());
		_sb_.append("=(");
		_sb_.append(this.providerid).append(",");
		_sb_.append(this.sessionid).append(",");
		_sb_.append(this.classindex).append(",");
		_sb_.append(this.instanceindex).append(",");
		_sb_.append(this.controlindex).append(",");
		_sb_.append("B").append(this.controlparameter.size()).append(",");
		_sb_.append("T").append(this.stringdata.length()).append(",");
		_sb_.append(")");
		return _sb_.toString();
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}

}
