
package limax.provider.switcherprovider;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class Probe extends limax.net.Rpc<limax.switcherprovider.ProbeValue, limax.switcherprovider.ProbeValue> {
	@Override
	protected void onServer() {
		getResult().key = getArgument().key;
	}

	@Override
	protected void onClient() {
		// response handle
	}

	@Override
	protected void onTimeout() {
		// client only. when call by submit this method not reached。
	}

	@Override
	protected void onCancel() {
		// client only. when asynchronous closed。
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
