package limax.node.js.modules;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.script.Invocable;

import limax.node.js.Buffer.Data;
import limax.node.js.Buffer;
import limax.node.js.EventLoop;
import limax.node.js.Module;
import limax.node.js.modules.http.Header;
import limax.node.js.modules.http.HttpException;
import limax.node.js.modules.http.Message;
import limax.node.js.modules.http.MessageBuilder;
import limax.util.Pair;

public final class Http implements Module {
	private final static ByteBuffer EMPTY = ByteBuffer.allocate(0);
	private final Invocable invocable;

	public Http(EventLoop eventLoop) {
		this.invocable = eventLoop.getInvocable();
	}

	public class Agent {
		private int maxFreeSockets = 256;
		private int countFreeSockets = 0;
		private final Map<String, Deque<Pair<Long, Object>>> freeSockets = new HashMap<>();
		private final Map<String, Deque<Object>> sockets = new HashMap<>();
		private final Map<String, Deque<Object>> requests = new HashMap<>();

		public void reset() {
			maxFreeSockets = 256;
			countFreeSockets = 0;
			freeSockets.clear();
			sockets.clear();
			requests.clear();
		}

		public void setMaxFreeSockets(int n) {
			if (n >= 0)
				maxFreeSockets = n;
		}

		public int getMaxFreeSockets() {
			return maxFreeSockets;
		}

		public boolean addFreeSocket(String key, Object socket) {
			if (countFreeSockets > maxFreeSockets)
				return false;
			freeSockets.computeIfAbsent(key, k -> new ArrayDeque<>())
					.add(new Pair<>(System.currentTimeMillis(), socket));
			countFreeSockets++;
			return true;
		}

		public Object allocFreeSocket(String key) {
			Deque<Pair<Long, Object>> dq = freeSockets.get(key);
			if (dq == null)
				return null;
			Object socket = dq.getFirst().getValue();
			countFreeSockets--;
			if (dq.isEmpty())
				freeSockets.remove(key);
			return socket;
		}

		public Object[] getFreeSockets() {
			return freeSockets.values().stream().flatMap(dq -> dq.stream()).map(pair -> pair.getValue()).toArray();
		}

		public Object[] removeTimeoutFreeSockets(long timeout) {
			List<Object> sockets = new ArrayList<>();
			long ts = System.currentTimeMillis() - timeout;
			for (Iterator<Deque<Pair<Long, Object>>> itdq = freeSockets.values().iterator(); itdq.hasNext();) {
				Deque<Pair<Long, Object>> dq = itdq.next();
				for (Iterator<Pair<Long, Object>> it = dq.iterator(); it.hasNext();) {
					Pair<Long, Object> pair = it.next();
					if (pair.getKey() > ts)
						break;
					sockets.add(pair.getValue());
					it.remove();
				}
				if (dq.isEmpty())
					itdq.remove();
			}
			return sockets.toArray();
		}

		public void removeFreeSocket(String key, Object socket) {
			Deque<Pair<Long, Object>> dq = freeSockets.get(key);
			if (dq != null)
				for (Iterator<Pair<Long, Object>> it = dq.iterator(); it.hasNext();)
					if (it.next().getValue() == socket) {
						it.remove();
						return;
					}
		}

		public void addSocket(String key, Object socket) {
			sockets.computeIfAbsent(key, k -> new ArrayDeque<>()).add(socket);
		}

		public void removeSocket(String key, Object socket) {
			Deque<Object> dq = sockets.get(key);
			if (dq != null)
				dq.remove(socket);
		}

		public Object[] getSockets() {
			return sockets.values().stream().flatMap(dq -> dq.stream()).toArray();
		}

		public void addRequest(String key, Object request) {
			requests.computeIfAbsent(key, k -> new ArrayDeque<>()).add(request);
		}

		public void removeRequest(String key, Object request) {
			Deque<Object> dq = requests.get(key);
			if (dq != null)
				dq.remove(request);
		}

		public Object[] getRequests() {
			return requests.values().stream().flatMap(dq -> dq.stream()).toArray();
		}
	}

	private interface IncomingFilter {
		ByteBuffer update(ByteBuffer bb);
	}

	private static class LengthFilter implements IncomingFilter {
		private int length;

		LengthFilter(int length) {
			this.length = length;
		}

		@Override
		public ByteBuffer update(ByteBuffer bb) {
			int len = bb.remaining();
			if (len < length) {
				length -= len;
				return null;
			}
			if (len == length)
				return EMPTY;
			int limit = bb.position() + length;
			ByteBuffer remain = bb.duplicate();
			bb.limit(limit);
			remain.position(limit);
			return remain;
		}
	}

	private static class ChunkedFilter implements IncomingFilter {
		private enum Stage {
			WANT_LENGTH, WANT_LENGTH_LF, WANT_DATA, WANT_DATA_LF, WANT_CR, WANT_TRAILER, WANT_END
		}

		private final Header trailer = new Header();
		private final StringBuilder sb = new StringBuilder();
		private Stage stage = Stage.WANT_LENGTH;
		private int length = 0;

		private static void join(ByteBuffer bb, List<ByteBuffer> list) {
			bb.clear();
			list.forEach(b -> bb.put(b));
			bb.flip();
		}

		@Override
		public ByteBuffer update(ByteBuffer bb) {
			List<ByteBuffer> list = null;
			char c;
			while (bb.hasRemaining()) {
				switch (stage) {
				case WANT_LENGTH:
					while (bb.hasRemaining()) {
						int v = Character.digit((char) bb.get(), 16);
						if (v == -1) {
							stage = Stage.WANT_LENGTH_LF;
							break;
						} else {
							length = length * 16 + v;
						}
					}
					break;
				case WANT_LENGTH_LF:
					while (bb.hasRemaining()) {
						if ((char) bb.get() == '\n') {
							stage = length > 0 ? Stage.WANT_DATA : Stage.WANT_CR;
							break;
						}
					}
					break;
				case WANT_DATA:
					int len = bb.remaining();
					if (len <= length) {
						length -= len;
						if (list != null) {
							list.add(bb.duplicate());
							join(bb, list);
						}
						return null;
					}
					if (list == null)
						list = new ArrayList<>();
					int limit = bb.position() + length;
					ByteBuffer r = bb.duplicate();
					r.limit(limit);
					list.add(r);
					bb.position(limit);
					stage = Stage.WANT_DATA_LF;
					break;
				case WANT_DATA_LF:
					while (bb.hasRemaining()) {
						if ((char) bb.get() == '\n') {
							length = 0;
							stage = Stage.WANT_LENGTH;
							break;
						}
					}
					break;
				case WANT_CR:
					c = (char) bb.get();
					if (c == '\r') {
						stage = Stage.WANT_END;
					} else {
						stage = Stage.WANT_TRAILER;
						sb.append(c);
					}
					break;
				case WANT_TRAILER:
					while (bb.hasRemaining()) {
						c = (char) bb.get();
						if (c == '\n') {
							String line = sb.toString();
							sb.setLength(0);
							int pos = line.indexOf(':');
							if (pos != -1)
								trailer.set(line.substring(0, pos).trim(), line.substring(pos + 1).trim());
							stage = Stage.WANT_CR;
							break;
						} else if (c != '\r') {
							sb.append(c);
						}
					}
					break;
				case WANT_END:
					while (bb.hasRemaining()) {
						if ((char) bb.get() == '\n') {
							ByteBuffer remain = bb.hasRemaining() ? bb.duplicate() : EMPTY;
							if (list != null)
								join(bb, list);
							else
								bb.limit(bb.position());
							return remain;
						}
					}
					break;
				}
			}
			if (list != null)
				join(bb, list);
			return null;
		}

		Header getTrailer() {
			return trailer.isEmpty() ? null : trailer;
		}
	}

	public class Session {
		private final Object callback;
		private final boolean server;
		private MessageBuilder builder;
		private IncomingFilter ifilter;
		private ByteBuffer remain;

		Session(Object socket, boolean server, Object callback) {
			this.callback = callback;
			this.server = server;
			reset(EMPTY);
		}

		private void reset(ByteBuffer remain) {
			this.builder = new MessageBuilder(server);
			this.ifilter = null;
			this.remain = remain.remaining() > 0 ? remain : null;
		}

		public Object[] onData(Buffer buffer) {
			try {
				return _onData(buffer);
			} catch (Exception e) {
				return new Object[] { e instanceof HttpException ? ((HttpException) e).getCode() : e };
			}
		}

		private Object[] _onData(Buffer buffer) throws Exception {
			Data data;
			ByteBuffer in;
			if (remain != null) {
				in = buffer.toByteBuffer();
				ByteBuffer tmp = ByteBuffer.allocate(remain.remaining() + in.remaining());
				tmp.put(remain).put(in).flip();
				data = new Data(tmp);
			} else {
				data = buffer.toData();
			}
			builder.update(data.buf, data.off, data.len);
			Message message = builder.getMessage();
			ByteBuffer r = builder.getByteBuffer();
			if (message != null) {
				invocable.invokeMethod(callback, "call", null, message, r != null ? new Buffer(r) : Buffer.EMPTY);
				Header header = message.getHeader();
				String s;
				if ((s = header.get("Content-Length")) != null) {
					ifilter = new LengthFilter(Integer.parseInt(s));
				} else if ((s = header.get("Transfer-Encoding")) != null && s.toLowerCase().contains("chunked")) {
					ifilter = new ChunkedFilter();
				} else if ((s = header.get("Connection")) != null && s.toLowerCase().contains("close")) {
				} else {
					reset(EMPTY);
					return new Object[] { null, true };
				}
			}
			if (r != null && ifilter != null) {
				ByteBuffer remain = ifilter.update(r);
				if (remain != null) {
					reset(remain);
					return new Object[] { new Buffer(r), true,
							ifilter instanceof ChunkedFilter ? ((ChunkedFilter) ifilter).getTrailer() : null };
				}
			}
			return new Object[] { r == null || r.remaining() == 0 ? null : new Buffer(r), false };
		}
	}

	public Header createHeader() {
		return new Header();
	}

	public Agent createAgent() {
		return new Agent();
	}

	public Session createSession(Object socket, boolean server, Object callback) {
		return new Session(socket, server, callback);
	}

	public boolean ignoreCaseIncludes(Object a, Object b) {
		if (a instanceof String && b instanceof String) {
			String x = ((String) a).toLowerCase();
			String y = ((String) b).toLowerCase();
			return x.contains(y);
		}
		return false;
	}
}
