package limax.switcher.switcherauany;

import limax.switcher.ProviderListener;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class CheckProviderKey
		extends limax.net.Rpc<limax.switcherauany.CheckProviderKeyArg, limax.switcherauany.CheckProviderKeyRes> {
	@Override
	protected void onServer() {
		// request handle
	}

	@Override
	protected void onClient() {
		ProviderListener.getInstance().processResult(this);
	}

	@Override
	protected void onTimeout() {
		ProviderListener.getInstance().processTimeout(this);
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

	public CheckProviderKey() {
		super.setArgument(new limax.switcherauany.CheckProviderKeyArg());
		super.setResult(new limax.switcherauany.CheckProviderKeyRes());
	}

	public CheckProviderKey(limax.net.Rpc.Listener<limax.switcherauany.CheckProviderKeyArg, limax.switcherauany.CheckProviderKeyRes> listener) {
		super(listener);
		super.setArgument(new limax.switcherauany.CheckProviderKeyArg());
		super.setResult(new limax.switcherauany.CheckProviderKeyRes());
	}

	public CheckProviderKey(limax.switcherauany.CheckProviderKeyArg argument) {
		super.setArgument(argument);
		super.setResult(new limax.switcherauany.CheckProviderKeyRes());
	}

	public CheckProviderKey(limax.switcherauany.CheckProviderKeyArg argument, limax.net.Rpc.Listener<limax.switcherauany.CheckProviderKeyArg, limax.switcherauany.CheckProviderKeyRes> listener) {
		super(listener);
		super.setArgument(argument);
		super.setResult(new limax.switcherauany.CheckProviderKeyRes());
	}

	public long getTimeout() {
		return 5000;
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}
}
