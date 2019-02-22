package limax.http;

import java.nio.ByteBuffer;

interface ProtocolProcessor {
	static long DISABLE_INCOMING = -1;
	static long NO_RESET = -2;

	long process(ByteBuffer in);

	void shutdown(Throwable closeReason);
}
