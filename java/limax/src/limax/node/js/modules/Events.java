package limax.node.js.modules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.script.Invocable;

import limax.node.js.EventLoop;
import limax.node.js.Module;

public final class Events implements Module {
	private final Invocable invocable;

	public Events(EventLoop eventLoop) throws Exception {
		this.invocable = eventLoop.getInvocable();
	}

	@FunctionalInterface
	private interface ListenerConsumer {
		void accept(Object listener) throws Exception;
	}

	public class EventEmitter {
		private class Listener {
			private final Object listener;
			private final boolean once;

			Listener(Object listener, boolean once) {
				this.listener = listener;
				this.once = once;
			}
		}

		private final Object THIS;
		private final Map<Object, Deque<Listener>> map = new HashMap<>();
		private final Deque<Listener> newListener = new ArrayDeque<>();
		private final Deque<Listener> removeListener = new ArrayDeque<>();

		private Deque<Listener> get(Object eventName) {
			if (eventName.equals("newListener"))
				return newListener;
			if (eventName.equals("removeListener"))
				return removeListener;
			Deque<Listener> dq = map.get(eventName);
			if (dq == null)
				map.put(eventName, dq = new ArrayDeque<>());
			return dq;
		}

		public EventEmitter(Object THIS) {
			this.THIS = THIS;
		}

		public Object addListener(Object eventName, Object listener) throws Exception {
			return on(eventName, listener);
		}

		private void emit(Object eventName, Exception e) throws Exception {
			if (eventName.equals("error"))
				throw e;
			Deque<Listener> dq = map.get("error");
			if (dq == null)
				throw e;
			emit(dq, "error", obj -> invocable.invokeMethod(obj, "call", THIS, e, eventName));
		}

		private void emit(Deque<Listener> dq, Object eventName, ListenerConsumer consumer) throws Exception {
			for (Iterator<Listener> it = dq.iterator(); it.hasNext();) {
				Listener l = it.next();
				Object obj = l.listener;
				if (l.once) {
					it.remove();
					emit(removeListener, eventName, obj);
				}
				try {
					consumer.accept(obj);
				} catch (Exception e) {
					emit(eventName, e);
				}
			}
		}

		private void emit(Deque<Listener> dq, Object eventName, Object listener) throws Exception {
			emit(dq, eventName, obj -> invocable.invokeMethod(obj, "call", THIS, eventName, listener));
		}

		public boolean emit(Object eventName, Object arguments) throws Exception {
			Deque<Listener> dq = map.get(eventName);
			if (dq == null)
				return false;
			emit(dq, eventName, obj -> invocable.invokeMethod(obj, "apply", THIS, arguments));
			if (dq.isEmpty())
				map.remove(eventName);
			return true;
		}

		public List<Object> eventNames() {
			return new ArrayList<>(map.keySet());
		}

		public int getMaxListeners() {
			return Integer.MAX_VALUE;
		}

		public int listenerCount(Object eventName) {
			Deque<Listener> dq = map.get(eventName);
			return dq == null ? 0 : dq.size();
		}

		public List<Object> listeners(Object eventName) {
			Deque<Listener> dq = map.get(eventName);
			return dq == null ? Collections.emptyList() : dq.stream().map(l -> l.listener).collect(Collectors.toList());
		}

		public Object on(Object eventName, Object listener) throws Exception {
			emit(newListener, eventName, listener);
			get(eventName).add(new Listener(listener, false));
			return THIS;
		}

		public Object once(Object eventName, Object listener) throws Exception {
			emit(newListener, eventName, listener);
			get(eventName).add(new Listener(listener, true));
			return THIS;
		}

		public Object prependListener(Object eventName, Object listener) throws Exception {
			emit(newListener, eventName, listener);
			get(eventName).addFirst(new Listener(listener, false));
			return THIS;
		}

		public Object prependOnceListener(Object eventName, Object listener) throws Exception {
			emit(newListener, eventName, listener);
			get(eventName).addFirst(new Listener(listener, true));
			return THIS;
		}

		public Object removeAllListeners(Object eventName) {
			if (eventName == null) {
				map.forEach((k, v) -> {
					v.forEach(l -> {
						try {
							emit(removeListener, k, l.listener);
						} catch (Exception e) {
						}
					});
				});
				map.clear();
			} else
				map.remove(eventName).forEach(l -> {
					try {
						emit(removeListener, eventName, l.listener);
					} catch (Exception e) {
					}
				});
			return THIS;
		}

		public Object removeListener(Object eventName, Object listener) throws Exception {
			Deque<Listener> dq = map.get(eventName);
			if (dq == null)
				return THIS;
			for (Iterator<Listener> it = dq.iterator(); it.hasNext();) {
				Listener l = it.next();
				if (l.listener == listener) {
					it.remove();
					emit(removeListener, eventName, listener);
				}
			}
			return THIS;
		}

		public Object setMaxListeners(int n) {
			return THIS;
		}
	}

	public EventEmitter newEventEmitter(Object THIS) {
		return new EventEmitter(THIS);
	}
}
