package limax.endpoint;

import java.util.Collection;

public abstract class TemporaryView extends View {

	volatile int instanceindex;

	protected TemporaryView(ViewContext vc) {
		super(vc);
	}

	public final int getInstanceIndex() {
		return instanceindex;
	}

	protected abstract void onOpen(Collection<Long> sessionids);

	protected abstract void onClose();

	protected abstract void onAttach(long sessionid);

	protected abstract void detach(long sessionid, byte reason);

	protected abstract void onDetach(long sessionid, byte reason);

	void doClose() {
		super.doClose();
		onClose();
	}

	@Override
	public String toString() {
		return "[class = " + getClass().getName() + " ProviderId = " + getViewContext().getProviderId()
				+ " classindex = " + getClassIndex() + " instanceindex = " + instanceindex + "]";
	}
}
