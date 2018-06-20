package limax.net;

import limax.util.Closeable;

public interface Manager extends Closeable {

	void close(Transport transport);

	Listener getListener();

	Config getConfig();

	Manager getWrapperManager();
}
