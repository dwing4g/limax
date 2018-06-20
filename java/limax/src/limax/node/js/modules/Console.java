package limax.node.js.modules;

import java.util.Map;
import java.util.WeakHashMap;

import limax.node.js.EventLoop;
import limax.node.js.Module;

public final class Console implements Module {
	private final Map<Object, Long> timeMap = new WeakHashMap<>();

	public Console(EventLoop eventLoop) throws Exception {
	}

	public void time(Object label) {
		timeMap.put(label, System.nanoTime());
	}

	public Double timeEnd(Object label) {
		Long start = timeMap.remove(label);
		return start == null ? null : (System.nanoTime() - start) / 1000000.0;
	}

}
