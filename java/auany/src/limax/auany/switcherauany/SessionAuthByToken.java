package limax.auany.switcherauany;

import limax.auany.__ProtocolProcessManager;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class SessionAuthByToken
		extends limax.net.Rpc<limax.switcherauany.AuanyAuthArg, limax.switcherauany.AuanyAuthRes> {
	@Override
	public void process() throws Exception {
		__ProtocolProcessManager.process(this);
	}

	@Override
	protected void onClient() {
		// response handle
	}

	@Override
	protected void onTimeout() {
		// client only. when call by submit this method not reached.
	}

	@Override
	protected void onCancel() {
		// client only. when asynchronous closed.
	}

	// {{{ XMLGEN_DEFINE_BEGIN
	// {{{ DO NOT EDIT THIS
	public static int TYPE;

	public int getType() {
		return TYPE;
	}

	public SessionAuthByToken() {
		super.setArgument(new limax.switcherauany.AuanyAuthArg());
		super.setResult(new limax.switcherauany.AuanyAuthRes());
	}

	public SessionAuthByToken(limax.net.Rpc.Listener<limax.switcherauany.AuanyAuthArg, limax.switcherauany.AuanyAuthRes> listener) {
		super(listener);
		super.setArgument(new limax.switcherauany.AuanyAuthArg());
		super.setResult(new limax.switcherauany.AuanyAuthRes());
	}

	public SessionAuthByToken(limax.switcherauany.AuanyAuthArg argument) {
		super.setArgument(argument);
		super.setResult(new limax.switcherauany.AuanyAuthRes());
	}

	public SessionAuthByToken(limax.switcherauany.AuanyAuthArg argument, limax.net.Rpc.Listener<limax.switcherauany.AuanyAuthArg, limax.switcherauany.AuanyAuthRes> listener) {
		super(listener);
		super.setArgument(argument);
		super.setResult(new limax.switcherauany.AuanyAuthRes());
	}

	public long getTimeout() {
		return 5000;
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}
}
