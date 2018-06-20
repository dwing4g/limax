
package limax.switcher.switcherauany;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class OnlineAnnounce extends limax.net.Protocol {
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

    public String key; 
    public java.util.ArrayList<Integer> nativeIds; 
    public java.util.ArrayList<Integer> wsIds; 
    public java.util.ArrayList<Integer> wssIds; 
    public java.util.HashMap<Integer, Boolean> pvids; 

	public OnlineAnnounce() {
		key = "";
		nativeIds = new java.util.ArrayList<Integer>();
		wsIds = new java.util.ArrayList<Integer>();
		wssIds = new java.util.ArrayList<Integer>();
		pvids = new java.util.HashMap<Integer, Boolean>();
	}

	public OnlineAnnounce(String _key_, java.util.ArrayList<Integer> _nativeIds_, java.util.ArrayList<Integer> _wsIds_, java.util.ArrayList<Integer> _wssIds_, java.util.HashMap<Integer, Boolean> _pvids_) {
		this.key = _key_;
		this.nativeIds = _nativeIds_;
		this.wsIds = _wsIds_;
		this.wssIds = _wssIds_;
		this.pvids = _pvids_;
	}

	@Override
	public limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {
		_os_.marshal(this.key);
		_os_.marshal_size(this.nativeIds.size());
		for (Integer _v_ : this.nativeIds) {
			_os_.marshal(_v_);
		}
		_os_.marshal_size(this.wsIds.size());
		for (Integer _v_ : this.wsIds) {
			_os_.marshal(_v_);
		}
		_os_.marshal_size(this.wssIds.size());
		for (Integer _v_ : this.wssIds) {
			_os_.marshal(_v_);
		}
		_os_.marshal_size(this.pvids.size());
		for (java.util.Map.Entry<Integer, Boolean> _e_ : this.pvids.entrySet()) {
			_os_.marshal(_e_.getKey());
			_os_.marshal(_e_.getValue());
		}
		return _os_;
	}

	@Override
	public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {
		this.key = _os_.unmarshal_String();
		for(int _i_ = _os_.unmarshal_size(); _i_ > 0; --_i_) {
			int _v_ = _os_.unmarshal_int();
			this.nativeIds.add(_v_);
		}
		for(int _i_ = _os_.unmarshal_size(); _i_ > 0; --_i_) {
			int _v_ = _os_.unmarshal_int();
			this.wsIds.add(_v_);
		}
		for(int _i_ = _os_.unmarshal_size(); _i_ > 0; --_i_) {
			int _v_ = _os_.unmarshal_int();
			this.wssIds.add(_v_);
		}
		for(int _i_ = _os_.unmarshal_size(); _i_ > 0; --_i_) {
			int _k_ = _os_.unmarshal_int();
			boolean _v_ = _os_.unmarshal_boolean();
			this.pvids.put(_k_, _v_);
		}
		return _os_;
	}

	@Override
	public String toString() {
		StringBuilder _sb_ = new StringBuilder(super.toString());
		_sb_.append("=(");
		_sb_.append("T").append(this.key.length()).append(",");
		_sb_.append(this.nativeIds).append(",");
		_sb_.append(this.wsIds).append(",");
		_sb_.append(this.wssIds).append(",");
		_sb_.append(this.pvids).append(",");
		_sb_.append(")");
		return _sb_.toString();
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}

}
