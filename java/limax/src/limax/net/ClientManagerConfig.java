package limax.net;

import java.net.SocketAddress;

public interface ClientManagerConfig extends ManagerConfig {
	SocketAddress getPeerAddress();

	boolean isAutoReconnect();

	long getConnectTimeout();
}
