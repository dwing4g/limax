package limax.endpoint.variant;

import java.util.Collection;

public interface TemporaryViewHandler {

	void onOpen(VariantView view, Collection<Long> sessionids);

	void onClose(VariantView view);

	void onAttach(VariantView view, long sessionid);

	void onDetach(VariantView view, long sessionid, int reason);

}
