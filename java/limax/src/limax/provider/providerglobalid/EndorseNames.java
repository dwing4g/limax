
package limax.provider.providerglobalid;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class EndorseNames
		extends limax.net.Rpc<limax.providerglobalid.NamesEndorse, limax.providerglobalid.NameResponse> {
	@Override
	protected void onServer() {
		// request handle
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

	public EndorseNames() {
		super.setArgument(new limax.providerglobalid.NamesEndorse());
		super.setResult(new limax.providerglobalid.NameResponse());
	}

	public EndorseNames(limax.net.Rpc.Listener<limax.providerglobalid.NamesEndorse, limax.providerglobalid.NameResponse> listener) {
		super(listener);
		super.setArgument(new limax.providerglobalid.NamesEndorse());
		super.setResult(new limax.providerglobalid.NameResponse());
	}

	public EndorseNames(limax.providerglobalid.NamesEndorse argument) {
		super.setArgument(argument);
		super.setResult(new limax.providerglobalid.NameResponse());
	}

	public EndorseNames(limax.providerglobalid.NamesEndorse argument, limax.net.Rpc.Listener<limax.providerglobalid.NamesEndorse, limax.providerglobalid.NameResponse> listener) {
		super(listener);
		super.setArgument(argument);
		super.setResult(new limax.providerglobalid.NameResponse());
	}

	public long getTimeout() {
		return 2000;
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}
}
