
package limax.provider.switcherprovider;

import limax.provider.__ProtocolProcessManager;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class Pay extends limax.net.Protocol {
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

    public int payid; 
    public long serial; 
    public long sessionid; 
    public int product; 
    public int price; 
    public int count; 

	public Pay() {
	}

	public Pay(int _payid_, long _serial_, long _sessionid_, int _product_, int _price_, int _count_) {
		this.payid = _payid_;
		this.serial = _serial_;
		this.sessionid = _sessionid_;
		this.product = _product_;
		this.price = _price_;
		this.count = _count_;
	}

	@Override
	public limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {
		_os_.marshal(this.payid);
		_os_.marshal(this.serial);
		_os_.marshal(this.sessionid);
		_os_.marshal(this.product);
		_os_.marshal(this.price);
		_os_.marshal(this.count);
		return _os_;
	}

	@Override
	public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {
		this.payid = _os_.unmarshal_int();
		this.serial = _os_.unmarshal_long();
		this.sessionid = _os_.unmarshal_long();
		this.product = _os_.unmarshal_int();
		this.price = _os_.unmarshal_int();
		this.count = _os_.unmarshal_int();
		return _os_;
	}

	@Override
	public String toString() {
		StringBuilder _sb_ = new StringBuilder(super.toString());
		_sb_.append("=(");
		_sb_.append(this.payid).append(",");
		_sb_.append(this.serial).append(",");
		_sb_.append(this.sessionid).append(",");
		_sb_.append(this.product).append(",");
		_sb_.append(this.price).append(",");
		_sb_.append(this.count).append(",");
		_sb_.append(")");
		return _sb_.toString();
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}

}
