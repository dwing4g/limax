
package limax.switcher.switcherprovider;

import limax.switcher.SwitcherListener;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class Kick extends limax.net.Protocol {
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

    public long sessionid; 
    public int error; 

	public Kick() {
	}

	public Kick(long _sessionid_, int _error_) {
		this.sessionid = _sessionid_;
		this.error = _error_;
	}

	@Override
	public limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {
		_os_.marshal(this.sessionid);
		_os_.marshal(this.error);
		return _os_;
	}

	@Override
	public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {
		this.sessionid = _os_.unmarshal_long();
		this.error = _os_.unmarshal_int();
		return _os_;
	}

	@Override
	public String toString() {
		StringBuilder _sb_ = new StringBuilder(super.toString());
		_sb_.append("=(");
		_sb_.append(this.sessionid).append(",");
		_sb_.append(this.error).append(",");
		_sb_.append(")");
		return _sb_.toString();
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}

}
