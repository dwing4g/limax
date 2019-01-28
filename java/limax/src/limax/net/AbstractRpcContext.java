package limax.net;

import java.util.HashMap;
import java.util.Map;

import limax.util.Closeable;

public abstract class AbstractRpcContext implements SupportRpcContext {

	private int serialId = 0; // 1~0x7fffffff. see Rpc
	private final Map<Integer, Closeable> contexts = new HashMap<Integer, Closeable>();

	@Override
	public void closeAllContexts() {
		synchronized (contexts) {
			for (Closeable c : contexts.values())
				try {
					c.close();
				} catch (Throwable t) {
				}
			contexts.clear();
		}
	}

	@Override
	public Closeable removeContext(int sid) {
		synchronized (contexts) {
			return contexts.remove(sid);
		}
	}

	@Override
	public <T> T removeContext(int sid, T hint) {
		synchronized (contexts) {
			@SuppressWarnings("unchecked")
			T a = (T) contexts.remove(sid);
			return a;
		}
	}

	@Override
	public int addContext(Closeable obj) {
		synchronized (contexts) {
			do {
				++serialId;
				if (serialId <= 0) // leftmost bit reserved for rpc
					serialId = 1;
				if (!contexts.containsKey(serialId)) {
					contexts.put(serialId, obj);
					return serialId;
				}
			} while (true);
		}
	}
}
