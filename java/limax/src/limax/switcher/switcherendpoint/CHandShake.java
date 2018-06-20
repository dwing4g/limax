
package limax.switcher.switcherendpoint;

import limax.switcher.SwitcherListener;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class CHandShake extends limax.net.Protocol {
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

    public byte dh_group; 
    public limax.codec.Octets dh_data; 

	public CHandShake() {
		dh_data = new limax.codec.Octets();
	}

	public CHandShake(byte _dh_group_, limax.codec.Octets _dh_data_) {
		this.dh_group = _dh_group_;
		this.dh_data = _dh_data_;
	}

	@Override
	public limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {
		_os_.marshal(this.dh_group);
		_os_.marshal(this.dh_data);
		return _os_;
	}

	@Override
	public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {
		this.dh_group = _os_.unmarshal_byte();
		this.dh_data = _os_.unmarshal_Octets();
		return _os_;
	}

	@Override
	public String toString() {
		StringBuilder _sb_ = new StringBuilder(super.toString());
		_sb_.append("=(");
		_sb_.append(this.dh_group).append(",");
		_sb_.append("B").append(this.dh_data.size()).append(",");
		_sb_.append(")");
		return _sb_.toString();
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}

}
