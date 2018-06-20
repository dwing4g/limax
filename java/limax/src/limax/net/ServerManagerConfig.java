package limax.net;

import java.net.SocketAddress;

import javax.net.ssl.SSLContext;

import limax.util.Limit;

public interface ServerManagerConfig extends ManagerConfig {

	SocketAddress getLocalAddress();

	int getBacklog();

	Limit getTransportLimit();

	boolean isAutoListen();

	boolean isWebSocketEnabled();

	SSLContext getSSLContext();
}
