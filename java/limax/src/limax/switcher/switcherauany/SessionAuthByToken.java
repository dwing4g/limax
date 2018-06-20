package limax.switcher.switcherauany;

import limax.switcher.SwitcherListener;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class SessionAuthByToken
		extends limax.net.Rpc<limax.switcherauany.AuanyAuthArg, limax.switcherauany.AuanyAuthRes> {
	@Override
	protected void onServer() {
		// request handle
	}

	@Override
	protected void onClient() {
		SwitcherListener.getInstance().processResult(this);
	}

	@Override
	protected void onTimeout() {
		SwitcherListener.getInstance().processTimeout(this);
	}

	@Override
	protected void onCancel() {
		onTimeout();
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
