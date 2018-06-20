package limax.endpoint;

import limax.endpoint.ViewContext.Type;
import limax.net.ClientManager;

public interface EndpointManager extends ClientManager {

	long getSessionId();

	long getAccountFlags();

	ViewContext getViewContext(int pvid, Type type);
}
