package limax.net;

/**
 * manager capability
 */
public interface SupportRpcContext {
	void closeAllContexts();

	java.io.Closeable removeContext(int sid);

	<T> T removeContext(int sid, T hint);

	int addContext(java.io.Closeable obj);
}
