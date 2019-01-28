package limax.util.transpond;

import java.net.SocketAddress;

public interface FlowControlProcessor {

	void shutdown(boolean eventually) throws Exception;

	boolean startup(FlowControlTask task, SocketAddress local, SocketAddress peer) throws Exception;

	void sendDataTo(byte[] data) throws Exception;

	void sendDone(long leftsize) throws Exception;
}
