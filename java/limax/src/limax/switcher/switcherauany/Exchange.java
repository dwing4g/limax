
package limax.switcher.switcherauany;

import limax.switcher.SwitcherListener;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class Exchange extends limax.net.Protocol {
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

    public static final int UPLOAD_LMKDATA = 0; 
    public static final int CONFIG_LMKMASQUERADE = 1; 

    public int type; 
    public limax.codec.Octets data; 

	public Exchange() {
		data = new limax.codec.Octets();
	}

	public Exchange(int _type_, limax.codec.Octets _data_) {
		this.type = _type_;
		this.data = _data_;
	}

	@Override
	public limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {
		_os_.marshal(this.type);
		_os_.marshal(this.data);
		return _os_;
	}

	@Override
	public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {
		this.type = _os_.unmarshal_int();
		this.data = _os_.unmarshal_Octets();
		return _os_;
	}

	@Override
	public String toString() {
		StringBuilder _sb_ = new StringBuilder(super.toString());
		_sb_.append("=(");
		_sb_.append(this.type).append(",");
		_sb_.append("B").append(this.data.size()).append(",");
		_sb_.append(")");
		return _sb_.toString();
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}

}

