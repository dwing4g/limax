
package limax.provider.providerglobalid;

// {{{ XMLGEN_IMPORT_BEGIN
// {{{ DO NOT EDIT THIS

// DO NOT EDIT THIS }}}
// XMLGEN_IMPORT_END }}}

public class RequestId extends limax.net.Rpc<limax.providerglobalid.Group, limax.providerglobalid.Id> {
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

	public RequestId() {
		super.setArgument(new limax.providerglobalid.Group());
		super.setResult(new limax.providerglobalid.Id());
	}

	public RequestId(limax.net.Rpc.Listener<limax.providerglobalid.Group, limax.providerglobalid.Id> listener) {
		super(listener);
		super.setArgument(new limax.providerglobalid.Group());
		super.setResult(new limax.providerglobalid.Id());
	}

	public RequestId(limax.providerglobalid.Group argument) {
		super.setArgument(argument);
		super.setResult(new limax.providerglobalid.Id());
	}

	public RequestId(limax.providerglobalid.Group argument, limax.net.Rpc.Listener<limax.providerglobalid.Group, limax.providerglobalid.Id> listener) {
		super(listener);
		super.setArgument(argument);
		super.setResult(new limax.providerglobalid.Id());
	}

	public long getTimeout() {
		return 2000;
	}

	// DO NOT EDIT THIS }}}
	// XMLGEN_DEFINE_END }}}
}
