
package limax.provider.providerendpoint;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class SyncViewToClients extends limax.net.Protocol {
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

    public static final int DT_VIEW_DATA = 0; 
    public static final int DT_TEMPORARY_INIT_DATA = 1; 
    public static final int DT_TEMPORARY_DATA = 2; 
    public static final int DT_TEMPORARY_ATTACH = 3; 
    public static final int DT_TEMPORARY_DETACH = 4; 
    public static final int DT_TEMPORARY_CLOSE = 5; 

    public int providerid; 
    public java.util.ArrayList<Long> sessionids; 
    public short classindex; 
    public int instanceindex; 
    public byte synctype; 
    public java.util.ArrayList<limax.providerendpoint.ViewVariableData> vardatas; 
    public java.util.ArrayList<limax.providerendpoint.ViewMemberData> members; 
    public String stringdata; 

	public SyncViewToClients() {
		sessionids = new java.util.ArrayList<Long>();
		vardatas = new java.util.ArrayList<limax.providerendpoint.ViewVariableData>();
		members = new java.util.ArrayList<limax.providerendpoint.ViewMemberData>();
		stringdata = "";
	}

	public SyncViewToClients(int _providerid_, java.util.ArrayList<Long> _sessionids_, short _classindex_, int _instanceindex_, byte _synctype_, java.util.ArrayList<limax.providerendpoint.ViewVariableData> _vardatas_, java.util.ArrayList<limax.providerendpoint.ViewMemberData> _members_, String _stringdata_) {
		this.providerid = _providerid_;
		this.sessionids = _sessionids_;
		this.classindex = _classindex_;
		this.instanceindex = _instanceindex_;
		this.synctype = _synctype_;
		this.vardatas = _vardatas_;
		this.members = _members_;
		this.stringdata = _stringdata_;
	}

	@Override
	public limax.codec.OctetsStream marshal(limax.codec.OctetsStream _os_) {
		_os_.marshal(this.providerid);
		_os_.marshal_size(this.sessionids.size());
		for (Long _v_ : this.sessionids) {
			_os_.marshal(_v_);
		}
		_os_.marshal(this.classindex);
		_os_.marshal(this.instanceindex);
		_os_.marshal(this.synctype);
		_os_.marshal_size(this.vardatas.size());
		for (limax.providerendpoint.ViewVariableData _v_ : this.vardatas) {
			_os_.marshal(_v_);
		}
		_os_.marshal_size(this.members.size());
		for (limax.providerendpoint.ViewMemberData _v_ : this.members) {
			_os_.marshal(_v_);
		}
		_os_.marshal(this.stringdata);
		return _os_;
	}

	@Override
	public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {
		this.providerid = _os_.unmarshal_int();
		for(int _i_ = _os_.unmarshal_size(); _i_ > 0; --_i_) {
			long _v_ = _os_.unmarshal_long();
			this.sessionids.add(_v_);
		}
		this.classindex = _os_.unmarshal_short();
		this.instanceindex = _os_.unmarshal_int();
		this.synctype = _os_.unmarshal_byte();
		for(int _i_ = _os_.unmarshal_size(); _i_ > 0; --_i_) {
			limax.providerendpoint.ViewVariableData _v_ = new limax.providerendpoint.ViewVariableData();
			_v_.unmarshal(_os_);
			this.vardatas.add(_v_);
		}
		for(int _i_ = _os_.unmarshal_size(); _i_ > 0; --_i_) {
			limax.providerendpoint.ViewMemberData _v_ = new limax.providerendpoint.ViewMemberData();
			_v_.unmarshal(_os_);
			this.members.add(_v_);
		}
		this.stringdata = _os_.unmarshal_String();
		return _os_;
	}

	@Override
	public String toString() {
		StringBuilder _sb_ = new StringBuilder(super.toString());
		_sb_.append("=(");
		_sb_.append(this.providerid).append(",");
		_sb_.append(this.sessionids).append(",");
		_sb_.append(this.classindex).append(",");
		_sb_.append(this.instanceindex).append(",");
		_sb_.append(this.synctype).append(",");
		_sb_.append(this.vardatas).append(",");
		_sb_.append(this.members).append(",");
		_sb_.append("T").append(this.stringdata.length()).append(",");
		_sb_.append(")");
		return _sb_.toString();
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}

}
