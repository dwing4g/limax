package limax.endpoint;

import limax.net.ClientListener;

public interface EndpointListener extends ClientListener {
	void onSocketConnected();

	void onKeyExchangeDone();

	void onKeepAlived(int ms);

	void onErrorOccured(int source, int code, Throwable exception);
}
