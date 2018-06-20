
package limax.switcher.providerendpoint;

import limax.switcher.SwitcherListener;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class Tunnel extends limax.net.Protocol {
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
    public int label; 
    public limax.codec.Octets data; 

	public Tunnel() {
		data = new limax.codec.Octets();
	}

	public Tunnel(int _providerid_, long _sessionid_, int _label_, limax.codec.Octets _data_) {
		this.providerid = _providerid_;
		this.sessionid = _sessionid_;
		this.label = _label_;
		this.data = _data_;
	}

	@Override
	public limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {
		_os_.marshal(this.providerid);
		_os_.marshal(this.sessionid);
		_os_.marshal(this.label);
		_os_.marshal(this.data);
		return _os_;
	}

	@Override
	public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {
		this.providerid = _os_.unmarshal_int();
		this.sessionid = _os_.unmarshal_long();
		this.label = _os_.unmarshal_int();
		this.data = _os_.unmarshal_Octets();
		return _os_;
	}

	@Override
	public String toString() {
		StringBuilder _sb_ = new StringBuilder(super.toString());
		_sb_.append("=(");
		_sb_.append(this.providerid).append(",");
		_sb_.append(this.sessionid).append(",");
		_sb_.append(this.label).append(",");
		_sb_.append("B").append(this.data.size()).append(",");
		_sb_.append(")");
		return _sb_.toString();
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}

}

