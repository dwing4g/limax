package limax.node.js.modules;

import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import limax.node.js.EventLoop;
import limax.node.js.Module;

public final class Util implements Module {

	public Util(EventLoop eventLoop) {
	}

	public Set<Object> createIdentityHashSet() {
		return Collections.newSetFromMap(new HashMap<>());
	}

	public String format(String fmt, Object[] args) {
		try {
			return String.format(fmt, args);
		} catch (Exception e) {
			return null;
		}
	}
}
