package limax.net;

import limax.util.Closeable;

/**
 * manager capability
 */
public interface SupportRpcContext {
	void closeAllContexts();

	Closeable removeContext(int sid);

	<T> T removeContext(int sid, T hint);

	int addContext(Closeable obj);
}
