package limax.net;

import java.net.SocketAddress;

public interface Transport {

	SocketAddress getPeerAddress();

	SocketAddress getLocalAddress();

	Object getSessionObject();

	void setSessionObject(Object obj);

	Manager getManager();

	Throwable getCloseReason();

}
