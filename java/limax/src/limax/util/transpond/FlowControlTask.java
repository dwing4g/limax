package limax.util.transpond;

import java.nio.ByteBuffer;

public interface FlowControlTask {

	void sendData(byte[] data);

	void sendData(ByteBuffer data);

	void disableReceive();

	void enableReceive();

	void closeSession();

}
