
package limax.provider.providerglobalid;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class RequestName
		extends limax.net.Rpc<limax.providerglobalid.NameRequest, limax.providerglobalid.NameResponse> {
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

	public RequestName() {
		super.setArgument(new limax.providerglobalid.NameRequest());
		super.setResult(new limax.providerglobalid.NameResponse());
	}

	public RequestName(limax.net.Rpc.Listener<limax.providerglobalid.NameRequest, limax.providerglobalid.NameResponse> listener) {
		super(listener);
		super.setArgument(new limax.providerglobalid.NameRequest());
		super.setResult(new limax.providerglobalid.NameResponse());
	}

	public RequestName(limax.providerglobalid.NameRequest argument) {
		super.setArgument(argument);
		super.setResult(new limax.providerglobalid.NameResponse());
	}

	public RequestName(limax.providerglobalid.NameRequest argument, limax.net.Rpc.Listener<limax.providerglobalid.NameRequest, limax.providerglobalid.NameResponse> listener) {
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
