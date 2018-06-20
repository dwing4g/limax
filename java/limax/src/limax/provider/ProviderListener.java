package limax.provider;

import limax.net.Listener;
import limax.net.Transport;

public interface ProviderListener extends Listener {

	void onTransportDuplicate(Transport transport) throws Exception;

}
