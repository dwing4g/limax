package limax.endpoint.switcherendpoint;

import limax.endpoint.__ProtocolProcessManager;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class OnlineAnnounce extends limax.net.Protocol {
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

    public int errorSource; 
    public int errorCode; 
    public long sessionid; 
    public long flags; 
    public java.util.HashMap<Integer, limax.defines.VariantDefines> variantdefines; 
    public String scriptdefines; 
    public limax.codec.Octets lmkdata; 

	public OnlineAnnounce() {
		variantdefines = new java.util.HashMap<Integer, limax.defines.VariantDefines>();
		scriptdefines = "";
		lmkdata = new limax.codec.Octets();
	}

	public OnlineAnnounce(int _errorSource_, int _errorCode_, long _sessionid_, long _flags_, java.util.HashMap<Integer, limax.defines.VariantDefines> _variantdefines_, String _scriptdefines_, limax.codec.Octets _lmkdata_) {
		this.errorSource = _errorSource_;
		this.errorCode = _errorCode_;
		this.sessionid = _sessionid_;
		this.flags = _flags_;
		this.variantdefines = _variantdefines_;
		this.scriptdefines = _scriptdefines_;
		this.lmkdata = _lmkdata_;
	}

	@Override
	public limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {
		_os_.marshal(this.errorSource);
		_os_.marshal(this.errorCode);
		_os_.marshal(this.sessionid);
		_os_.marshal(this.flags);
		_os_.marshal_size(this.variantdefines.size());
		for (java.util.Map.Entry<Integer, limax.defines.VariantDefines> _e_ : this.variantdefines.entrySet()) {
			_os_.marshal(_e_.getKey());
			_os_.marshal(_e_.getValue());
		}
		_os_.marshal(this.scriptdefines);
		_os_.marshal(this.lmkdata);
		return _os_;
	}

	@Override
	public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {
		this.errorSource = _os_.unmarshal_int();
		this.errorCode = _os_.unmarshal_int();
		this.sessionid = _os_.unmarshal_long();
		this.flags = _os_.unmarshal_long();
		for(int _i_ = _os_.unmarshal_size(); _i_ > 0; --_i_) {
			int _k_ = _os_.unmarshal_int();
			limax.defines.VariantDefines _v_ = new limax.defines.VariantDefines();
			_v_.unmarshal(_os_);
			this.variantdefines.put(_k_, _v_);
		}
		this.scriptdefines = _os_.unmarshal_String();
		this.lmkdata = _os_.unmarshal_Octets();
		return _os_;
	}

	@Override
	public String toString() {
		StringBuilder _sb_ = new StringBuilder(super.toString());
		_sb_.append("=(");
		_sb_.append(this.errorSource).append(",");
		_sb_.append(this.errorCode).append(",");
		_sb_.append(this.sessionid).append(",");
		_sb_.append(this.flags).append(",");
		_sb_.append(this.variantdefines).append(",");
		_sb_.append("T").append(this.scriptdefines.length()).append(",");
		_sb_.append("B").append(this.lmkdata.size()).append(",");
		_sb_.append(")");
		return _sb_.toString();
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}

}
