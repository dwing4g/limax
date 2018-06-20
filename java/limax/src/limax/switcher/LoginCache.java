package limax.switcher;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import limax.codec.CodecException;
import limax.codec.HmacSHA1;
import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.defines.ErrorCodes;
import limax.defines.ErrorSource;
import limax.endpoint.AuanyService;
import limax.net.Engine;
import limax.switcher.switcherauany.SessionAuthByToken;
import limax.switcherauany.AuanyAuthArg;
import limax.switcherauany.AuanyAuthRes;

final class LoginCache {
	private final Map<Key, Value> map;
	private final Map<RequestKey, Request> requests = new ConcurrentHashMap<>();
	private final Consumer<OctetsStream> send;
	private final Runnable close;

	LoginCache(String cacheGroup, int cacheCapacity) throws IOException {
		this.map = Collections.synchronizedMap(new LinkedHashMap<Key, Value>() {
			private static final long serialVersionUID = 6433227311691997245L;

			protected boolean removeEldestEntry(Map.Entry<Key, Value> eldest) {
				return size() > cacheCapacity;
			}
		});
		MulticastSocket socket = new MulticastSocket(20000);
		InetAddress group = InetAddress.getByName(cacheGroup);
		socket.joinGroup(group);
		socket.setTimeToLive(127);
		socket.setLoopbackMode(true);
		send = data -> {
			try {
				byte[] d = data.getBytes();
				socket.send(new DatagramPacket(d, d.length, group, 20000));
			} catch (Exception e) {
			}
		};
		AtomicBoolean closed = new AtomicBoolean();
		Engine.getProtocolScheduler().execute(() -> {
			byte[] buffer = new byte[4096];
			DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
			while (!closed.get()) {
				try {
					socket.receive(dp);
					dispatch(buffer);
				} catch (Exception e) {
				}
			}
		});
		close = () -> {
			closed.set(true);
			try {
				socket.leaveGroup(group);
			} catch (Exception e) {
			} finally {
				socket.close();
			}
		};
	}

	void close() {
		close.run();
	}

	private static class Key {
		private final byte[] username;
		private final byte[] sign;

		Key(SessionAuthByToken auth) throws CodecException {
			AuanyAuthArg arg = auth.getArgument();
			username = arg.username.getBytes();
			OctetsStream os = new OctetsStream().marshal(arg.token).marshal(arg.platflag);
			Map<Integer, Byte> pvids = new HashMap<>(arg.pvids);
			pvids.remove(AuanyService.providerId);
			os.marshal_size(pvids.size());
			pvids.forEach((k, v) -> os.marshal(k).marshal(v));
			byte[] data = os.getBytes();
			HmacSHA1 mac = new HmacSHA1(username, 0, username.length);
			mac.update(data, 0, data.length);
			this.sign = mac.digest();
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Key))
				return false;
			Key k = (Key) o;
			return Arrays.equals(username, k.username) && Arrays.equals(sign, k.sign);
		}

		@Override
		public int hashCode() {
			return Objects.hash(username, sign);
		}

		OctetsStream marshal(OctetsStream os) {
			return os.marshal(username).marshal(sign);
		}

		Key(OctetsStream os) throws MarshalException {
			this.username = os.unmarshal_bytes();
			this.sign = os.unmarshal_bytes();
		}
	}

	private static class Value {
		private final long sessionid;
		private final long mainid;
		private final String uid;
		private final long flags;

		Value(SessionAuthByToken auth) {
			AuanyAuthRes res = auth.getResult();
			this.sessionid = res.sessionid;
			this.mainid = res.mainid;
			this.uid = res.uid;
			this.flags = res.flags;
		}

		OctetsStream marshal(OctetsStream os) {
			return os.marshal(sessionid).marshal(mainid).marshal(uid).marshal(flags);
		}

		Value(OctetsStream os) throws MarshalException {
			this.sessionid = os.unmarshal_long();
			this.mainid = os.unmarshal_long();
			this.uid = os.unmarshal_String();
			this.flags = os.unmarshal_long();
		}
	}

	private static class RequestKey {
		private final static long startup = System.nanoTime();
		private final static AtomicLong idgenerator = new AtomicLong();
		private final long up;
		private final long id;

		RequestKey() {
			up = startup;
			id = idgenerator.getAndIncrement();
		}

		RequestKey(OctetsStream os) throws MarshalException {
			up = os.unmarshal_long();
			id = os.unmarshal_long();
		}

		OctetsStream marshal(OctetsStream os) {
			return os.marshal(up).marshal(id);
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof RequestKey))
				return false;
			RequestKey rk = (RequestKey) o;
			return rk.up == this.up && rk.id == this.id;
		}

		@Override
		public int hashCode() {
			return Objects.hash(up, id);
		}
	}

	private class Request {
		private final Consumer<Value> response;

		Request(SessionAuthByToken auth, Consumer<SessionAuthByToken> success, Consumer<SessionAuthByToken> timeout)
				throws CodecException {
			Key key = new Key(auth);
			RequestKey rk = new RequestKey();
			requests.put(rk, this);
			Future<?> future = Engine.getProtocolScheduler().schedule(() -> {
				if (requests.remove(rk) == this)
					timeout.accept(auth);
			} , auth.getTimeout(), TimeUnit.MILLISECONDS);
			this.response = value -> {
				future.cancel(false);
				if (requests.remove(rk) == this) {
					setResponse(auth, value);
					success.accept(auth);
				}
			};
			sendRequest(rk, key);
		}

		void response(Value value) {
			response.accept(value);
		}
	}

	private static final byte METHOD_REMOVE = 0;
	private static final byte METHOD_REQUEST = 1;
	private static final byte METHOD_RESPONSE = 2;

	private void sendRemove(Key key) {
		send.accept(key.marshal(new OctetsStream().marshal(METHOD_REMOVE)));
	}

	private void sendRequest(RequestKey rk, Key key) {
		send.accept(rk.marshal(key.marshal(new OctetsStream().marshal(METHOD_REQUEST))));
	}

	private void sendResponse(RequestKey rk, Value value) {
		send.accept(value.marshal(rk.marshal(new OctetsStream().marshal(METHOD_RESPONSE))));
	}

	void dispatch(byte[] data) throws MarshalException {
		OctetsStream os = OctetsStream.wrap(Octets.wrap(data));
		switch (os.unmarshal_byte()) {
		case METHOD_REMOVE:
			map.remove(new Key(os));
			break;
		case METHOD_REQUEST:
			Value v = map.get(new Key(os));
			if (v != null)
				sendResponse(new RequestKey(os), v);
			break;
		case METHOD_RESPONSE:
			Request r = requests.get(new RequestKey(os));
			if (r != null)
				r.response(new Value(os));
		}
	}

	private static void setResponse(SessionAuthByToken auth, Value value) {
		AuanyAuthRes res = auth.getResult();
		res.errorSource = ErrorSource.LIMAX;
		res.errorCode = ErrorCodes.SUCCEED;
		res.sessionid = value.sessionid;
		res.mainid = value.mainid;
		res.uid = value.uid;
		res.flags = value.flags;
	}

	void update(SessionAuthByToken auth) {
		try {
			Key key = new Key(auth);
			map.put(key, new Value(auth));
			sendRemove(key);
		} catch (CodecException e) {
		}
	}

	void request(SessionAuthByToken auth, Consumer<SessionAuthByToken> success, Consumer<SessionAuthByToken> timeout) {
		try {
			Key key = new Key(auth);
			Value value = map.get(key);
			if (value != null) {
				setResponse(auth, value);
				success.accept(auth);
			} else {
				new Request(auth, success, timeout);
			}
		} catch (CodecException e) {
			timeout.accept(auth);
		}
	}
}
