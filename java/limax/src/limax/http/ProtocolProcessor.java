package limax.http;

import java.nio.ByteBuffer;

interface ProtocolProcessor {
	void process(ByteBuffer in) throws Exception;

	void shutdown(Throwable closeReason);
}
