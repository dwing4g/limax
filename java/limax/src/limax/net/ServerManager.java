package limax.net;

import java.io.IOException;

public interface ServerManager extends Manager {
	boolean isListening();

	void openListen() throws IOException;

	void closeListen() throws IOException;
}
