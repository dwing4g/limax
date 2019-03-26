package limax.http;

import java.nio.ByteBuffer;

interface ProtocolProcessor {
	void process(ByteBuffer in);

	void shutdown(Throwable closeReason);
}
