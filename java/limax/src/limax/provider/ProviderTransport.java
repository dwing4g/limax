package limax.provider;

import java.net.SocketAddress;

import limax.net.Transport;

public interface ProviderTransport extends Transport {
	SocketAddress getReportAddress();

	long getSessionId();

	long getMainId();

	String getUid();

	LoginData getLoginData();

	long getAccountFlags();

	boolean isUseStatic();

	boolean isUseVariant();

	boolean isUseScript();

	boolean isWebSocket();

	boolean isStateless();
}
