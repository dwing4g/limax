
package limax.switcher.switcherauany;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class JSONPublish extends limax.net.Protocol {
	@Override
	public void process() {
		// protocol handle
	}

	// {{{ XMLGEN_DEFINE_BEGIN
	// {{{ DO NOT EDIT THIS
	public static int TYPE;

	public int getType() {
		return TYPE;
	}

    public int pvid; 
    public String json; 

	public JSONPublish() {
		json = "";
	}

	public JSONPublish(int _pvid_, String _json_) {
		this.pvid = _pvid_;
		this.json = _json_;
	}

	@Override
	public limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {
		_os_.marshal(this.pvid);
		_os_.marshal(this.json);
		return _os_;
	}

	@Override
	public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {
		this.pvid = _os_.unmarshal_int();
		this.json = _os_.unmarshal_String();
		return _os_;
	}

	@Override
	public String toString() {
		StringBuilder _sb_ = new StringBuilder(super.toString());
		_sb_.append("=(");
		_sb_.append(this.pvid).append(",");
		_sb_.append("T").append(this.json.length()).append(",");
		_sb_.append(")");
		return _sb_.toString();
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}

}
