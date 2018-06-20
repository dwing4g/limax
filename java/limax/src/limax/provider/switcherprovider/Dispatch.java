
package limax.provider.switcherprovider;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class Dispatch extends limax.net.Protocol {
	@Override
	public void process() {
	}

	// {{{ XMLGEN_DEFINE_BEGIN
	// {{{ DO NOT EDIT THIS
	public static int TYPE;

	public int getType() {
		return TYPE;
	}

    public long sessionid; 
    public int ptype; 
    public limax.codec.Octets pdata; 

	public Dispatch() {
		pdata = new limax.codec.Octets();
	}

	public Dispatch(long _sessionid_, int _ptype_, limax.codec.Octets _pdata_) {
		this.sessionid = _sessionid_;
		this.ptype = _ptype_;
		this.pdata = _pdata_;
	}

	@Override
	public limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {
		_os_.marshal(this.sessionid);
		_os_.marshal(this.ptype);
		_os_.marshal(this.pdata);
		return _os_;
	}

	@Override
	public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {
		this.sessionid = _os_.unmarshal_long();
		this.ptype = _os_.unmarshal_int();
		this.pdata = _os_.unmarshal_Octets();
		return _os_;
	}

	@Override
	public String toString() {
		StringBuilder _sb_ = new StringBuilder(super.toString());
		_sb_.append("=(");
		_sb_.append(this.sessionid).append(",");
		_sb_.append(this.ptype).append(",");
		_sb_.append("B").append(this.pdata.size()).append(",");
		_sb_.append(")");
		return _sb_.toString();
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}

}
