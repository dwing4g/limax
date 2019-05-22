package limax.http;

import java.nio.ByteBuffer;

public interface CustomSender {
	void send(ByteBuffer bb);

	void sendFinal(long timeout);
}