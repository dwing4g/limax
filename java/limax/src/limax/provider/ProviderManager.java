package limax.provider;

import limax.net.ServerManager;

public interface ProviderManager extends ServerManager {
	void close(long sessionid);

	void close(long sessionid, int reason);

	void close(ProviderTransport transport, int reason);
}
