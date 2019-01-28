package limax.net.io;

import java.net.SocketAddress;

public interface NetProcessor {
	void process(byte[] in) throws Exception;

	void shutdown(Throwable closeReason);

	boolean startup(NetTask nettask, SocketAddress local, SocketAddress peer) throws Exception;
}
