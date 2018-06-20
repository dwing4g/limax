package limax.net.io;

interface SSLSwitcher {
	void attach(String host, int port, boolean clientMode, byte[] sendBeforeHandshake);

	void detach();
}
