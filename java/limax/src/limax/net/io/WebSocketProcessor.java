package limax.net.io;

import java.net.SocketAddress;

public interface WebSocketProcessor {
	void process(String in) throws Exception;

	void process(byte[] in) throws Exception;

	void process(int code, String reason) throws Exception;

	void shutdown(boolean eventually, Throwable closeReason);

	void setup(WebSocketTask nettask);

	boolean setup(SocketAddress local, WebSocketAddress peer) throws Exception;
}
