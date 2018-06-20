
package limax.switcher.switcherprovider;

import limax.switcher.ProviderListener;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class PayAck extends limax.net.Protocol {
	@Override
	public void process() {
		ProviderListener.getInstance().process(this);
	}

	// {{{ XMLGEN_DEFINE_BEGIN
	// {{{ DO NOT EDIT THIS
	public static int TYPE;

	public int getType() {
		return TYPE;
	}

    public int payid; 
    public long serial; 

	public PayAck() {
	}

	public PayAck(int _payid_, long _serial_) {
		this.payid = _payid_;
		this.serial = _serial_;
	}

	@Override
	public limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {
		_os_.marshal(this.payid);
		_os_.marshal(this.serial);
		return _os_;
	}

	@Override
	public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {
		this.payid = _os_.unmarshal_int();
		this.serial = _os_.unmarshal_long();
		return _os_;
	}

	@Override
	public String toString() {
		StringBuilder _sb_ = new StringBuilder(super.toString());
		_sb_.append("=(");
		_sb_.append(this.payid).append(",");
		_sb_.append(this.serial).append(",");
		_sb_.append(")");
		return _sb_.toString();
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}

}
