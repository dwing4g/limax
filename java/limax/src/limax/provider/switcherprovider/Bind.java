
package limax.provider.switcherprovider;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class Bind extends limax.net.Protocol {
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

    public static final int PS_VARIANT_SUPPORTED = 1; 
    public static final int PS_VARIANT_ENABLED = 2; 
    public static final int PS_SCRIPT_SUPPORTED = 4; 
    public static final int PS_SCRIPT_ENABLED = 8; 
    public static final int PS_STATELESS = 16; 
    public static final int PS_PAY_SUPPORTED = 32; 
    public static final int PS_LOGINDATA_SUPPORTED = 64; 

    public int pvid; 
    public String pvkey; 
    public java.util.HashMap<Integer, Integer> pinfos; 
    public int capability; 
    public limax.defines.VariantDefines variantdefines; 
    public String scriptdefines; 
    public String json; 

	public Bind() {
		pvkey = "";
		pinfos = new java.util.HashMap<Integer, Integer>();
		variantdefines = new limax.defines.VariantDefines();
		scriptdefines = "";
		json = "";
	}

	public Bind(int _pvid_, String _pvkey_, java.util.HashMap<Integer, Integer> _pinfos_, int _capability_, limax.defines.VariantDefines _variantdefines_, String _scriptdefines_, String _json_) {
		this.pvid = _pvid_;
		this.pvkey = _pvkey_;
		this.pinfos = _pinfos_;
		this.capability = _capability_;
		this.variantdefines = _variantdefines_;
		this.scriptdefines = _scriptdefines_;
		this.json = _json_;
	}

	@Override
	public limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {
		_os_.marshal(this.pvid);
		_os_.marshal(this.pvkey);
		_os_.marshal_size(this.pinfos.size());
		for (java.util.Map.Entry<Integer, Integer> _e_ : this.pinfos.entrySet()) {
			_os_.marshal(_e_.getKey());
			_os_.marshal(_e_.getValue());
		}
		_os_.marshal(this.capability);
		_os_.marshal(this.variantdefines);
		_os_.marshal(this.scriptdefines);
		_os_.marshal(this.json);
		return _os_;
	}

	@Override
	public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {
		this.pvid = _os_.unmarshal_int();
		this.pvkey = _os_.unmarshal_String();
		for(int _i_ = _os_.unmarshal_size(); _i_ > 0; --_i_) {
			int _k_ = _os_.unmarshal_int();
			int _v_ = _os_.unmarshal_int();
			this.pinfos.put(_k_, _v_);
		}
		this.capability = _os_.unmarshal_int();
		this.variantdefines.unmarshal(_os_);
		this.scriptdefines = _os_.unmarshal_String();
		this.json = _os_.unmarshal_String();
		return _os_;
	}

	@Override
	public String toString() {
		StringBuilder _sb_ = new StringBuilder(super.toString());
		_sb_.append("=(");
		_sb_.append(this.pvid).append(",");
		_sb_.append("T").append(this.pvkey.length()).append(",");
		_sb_.append(this.pinfos).append(",");
		_sb_.append(this.capability).append(",");
		_sb_.append(this.variantdefines).append(",");
		_sb_.append("T").append(this.scriptdefines.length()).append(",");
		_sb_.append("T").append(this.json.length()).append(",");
		_sb_.append(")");
		return _sb_.toString();
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}

}
