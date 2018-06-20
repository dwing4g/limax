package limax.endpoint.auanyviews;

import limax.endpoint.ViewContext;
import limax.endpoint.__ProtocolProcessManager;

public final class ServiceResult extends _ServiceResult {
	private ServiceResult(ViewContext vc) {
		super(vc);
	}

	protected void onOpen(java.util.Collection<Long> sessionids) {
		__ProtocolProcessManager.onResultViewOpen(this);
	}

	protected void onClose() {
	}

	protected void onAttach(long sessionid) {
	}

	protected void onDetach(long sessionid, byte reason) {
		if (reason >= 0) {
			// Application reason
		} else {
			// Connection abort reason
		}
	}

}
