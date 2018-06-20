
package limax.switcher.switcherprovider;

import limax.switcher.ProviderListener;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class Probe extends limax.net.Rpc<limax.switcherprovider.ProbeValue, limax.switcherprovider.ProbeValue> {
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

	public Probe() {
		super.setArgument(new limax.switcherprovider.ProbeValue());
		super.setResult(new limax.switcherprovider.ProbeValue());
	}

	public Probe(limax.net.Rpc.Listener<limax.switcherprovider.ProbeValue, limax.switcherprovider.ProbeValue> listener) {
		super(listener);
		super.setArgument(new limax.switcherprovider.ProbeValue());
		super.setResult(new limax.switcherprovider.ProbeValue());
	}

	public Probe(limax.switcherprovider.ProbeValue argument) {
		super.setArgument(argument);
		super.setResult(new limax.switcherprovider.ProbeValue());
	}

	public Probe(limax.switcherprovider.ProbeValue argument, limax.net.Rpc.Listener<limax.switcherprovider.ProbeValue, limax.switcherprovider.ProbeValue> listener) {
		super(listener);
		super.setArgument(argument);
		super.setResult(new limax.switcherprovider.ProbeValue());
	}

	public long getTimeout() {
		return 3000;
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}
}

