package limax.net;

import java.net.SocketAddress;

import limax.http.WebSocketAddress;
import limax.http.WebSocketTask;

public interface WebSocketProcessor {
	void process(String in) throws Exception;

	void process(byte[] in) throws Exception;

	void process(int code, String reason) throws Exception;

	void shutdown(Throwable closeReason);

	boolean startup(WebSocketTask nettask, SocketAddress local, WebSocketAddress peer) throws Exception;
}
