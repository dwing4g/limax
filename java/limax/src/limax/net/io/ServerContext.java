package limax.net.io;

import java.io.IOException;

public interface ServerContext {
	interface NetTaskConstructor {
		NetTask newInstance(ServerContext context);

		String getServiceName();
	}

	void open() throws IOException;

	void close() throws IOException;

	boolean isOpen();
}
