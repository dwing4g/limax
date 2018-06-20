package limax.auany.auanyviews;

import limax.provider.TemporaryView;
import limax.provider.TemporaryView.Membership.AbortReason;
import limax.util.Trace;

public final class ServiceResult extends limax.auany.auanyviews._ServiceResult {
	String info;

	private ServiceResult(TemporaryView.CreateParameter param) {
		super(param);
	}

	@Override
	protected void onClose() {
		if (Trace.isInfoEnabled())
			Trace.info("ServiceResult onClose <" + info + ">");
	}

	@Override
	protected void onAttachAbort(long sessionid, AbortReason reason) {
		if (Trace.isInfoEnabled())
			Trace.info(
					"ServiceResult onAttachAbort sessionid = " + sessionid + " reason = " + reason + " <" + info + ">");
	}

	@Override
	protected void onDetachAbort(long sessionid, AbortReason reason) {
		if (Trace.isInfoEnabled())
			Trace.info(
					"ServiceResult onDetachAbort sessionid = " + sessionid + " reason = " + reason + " <" + info + ">");
	}

	@Override
	protected void onAttached(long sessionid) {
		if (Trace.isInfoEnabled())
			Trace.info("ServiceResult onAttached sessionid = " + sessionid + " <" + info + ">");
		destroyInstance();
	}

	@Override
	protected void onDetached(long sessionid, byte reason) {
		if (Trace.isInfoEnabled())
			Trace.info("ServiceResult onDetached sessionid = " + sessionid + " reason = " + reason + " <" + info + ">");
		if (reason >= 0) {
			// Application reason
		} else {
			// Connection abort reason
		}
	}

	@Override
	protected void onMessage(String message, long sessionid) {
	}

}
