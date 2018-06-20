package limax.net.io;

import java.net.SocketAddress;

public interface NetProcessor {
	void process(byte[] in) throws Exception;

	void shutdown(boolean eventually, Throwable closeReason);

	void setup(NetTask nettask);

	boolean setup(SocketAddress local, SocketAddress peer) throws Exception;
}
