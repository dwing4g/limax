
package limax.endpoint.switcherendpoint;

import limax.endpoint.__ProtocolProcessManager;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class SHandShake extends limax.net.Protocol {
	@Override
	public void process() throws Exception {
		__ProtocolProcessManager.process(this);
	}

	// {{{ XMLGEN_DEFINE_BEGIN
	// {{{ DO NOT EDIT THIS
	public static int TYPE;

	public int getType() {
		return TYPE;
	}

    public limax.codec.Octets dh_data; 
    public boolean s2cneedcompress; 
    public boolean c2sneedcompress; 

	public SHandShake() {
		dh_data = new limax.codec.Octets();
	}

	public SHandShake(limax.codec.Octets _dh_data_, boolean _s2cneedcompress_, boolean _c2sneedcompress_) {
		this.dh_data = _dh_data_;
		this.s2cneedcompress = _s2cneedcompress_;
		this.c2sneedcompress = _c2sneedcompress_;
	}

	@Override
	public limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {
		_os_.marshal(this.dh_data);
		_os_.marshal(this.s2cneedcompress);
		_os_.marshal(this.c2sneedcompress);
		return _os_;
	}

	@Override
	public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {
		this.dh_data = _os_.unmarshal_Octets();
		this.s2cneedcompress = _os_.unmarshal_boolean();
		this.c2sneedcompress = _os_.unmarshal_boolean();
		return _os_;
	}

	@Override
	public String toString() {
		StringBuilder _sb_ = new StringBuilder(super.toString());
		_sb_.append("=(");
		_sb_.append("B").append(this.dh_data.size()).append(",");
		_sb_.append(this.s2cneedcompress).append(",");
		_sb_.append(this.c2sneedcompress).append(",");
		_sb_.append(")");
		return _sb_.toString();
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}

}
