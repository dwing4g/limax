package limax.auany.local;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import limax.util.Dispatcher;
import limax.util.Helper;
import limax.util.Trace;

class Radius implements Authenticate {
	private final DatagramChannel channel;
	private final byte[] nasIdentifier;
	private final byte[] secret;
	private final BlockingQueue<Byte> slot = new LinkedBlockingQueue<>();
	private final Map<Byte, RequestContext> outstanding = new ConcurrentHashMap<>();
	private final long timeout;
	private final ScheduledExecutorService scheduler;
	private final Dispatcher dispatcher;
	private volatile boolean stopped = false;

	private class RequestContext {
		private final byte identifier;
		private final Consumer<Result> response;
		private final Future<?> future;
		private final byte[] authenticator = Helper.makeRandValues(16);

		void response(Result r) {
			if (outstanding.remove(identifier) != null) {
				slot.offer(identifier);
				response.accept(r);
				future.cancel(false);
			}
		}

		RequestContext(byte identifier, Consumer<Result> response, long timeout) {
			this.identifier = identifier;
			outstanding.put(identifier, this);
			this.response = response;
			this.future = scheduler.schedule(() -> response(Result.Timeout), timeout, TimeUnit.MILLISECONDS);
		}
	}

	private final static byte Access_Request = 1;
	private final static byte Access_Accept = 2;

	private final static byte User_Name = 1;
	private final static byte User_Password = 2;
	private final static byte NAS_Identifier = 32;
	private final static byte NAS_Port_Type = 61;
	private final static int NAS_Port_Type_VIRTUAL = 5;

	private void sendRequest(RequestContext rc, String username, String password) throws Exception {
		ByteBuffer r = ByteBuffer.allocateDirect(4096).order(ByteOrder.BIG_ENDIAN);
		byte[] name = username.getBytes(StandardCharsets.UTF_8);
		byte[] pass = makePassword(rc.authenticator, password.getBytes(StandardCharsets.UTF_8));
		r.put(Access_Request);
		r.put(rc.identifier);
		r.position(r.position() + 2);
		r.put(rc.authenticator);
		r.put(User_Name).put((byte) (2 + name.length)).put(name);
		r.put(User_Password).put((byte) (2 + pass.length)).put(pass);
		r.put(NAS_Identifier).put((byte) (2 + nasIdentifier.length)).put(nasIdentifier);
		r.put(NAS_Port_Type).put((byte) 6).putInt(NAS_Port_Type_VIRTUAL);
		r.putShort(2, (short) r.position()).flip();
		channel.write(r);
	}

	private byte[] makePassword(byte[] authenticator, byte[] pass) throws Exception {
		int len = (pass.length + 15) & ~15;
		if (len == 0)
			len = 16;
		byte[] r = Arrays.copyOf(pass, len);
		MessageDigest md = MessageDigest.getInstance("MD5");
		md.update(secret);
		md.update(authenticator);
		byte[] b = md.digest();
		int j = 0;
		for (int i = 0; i < 16;)
			r[j++] ^= b[i++];
		while (j < len) {
			md = MessageDigest.getInstance("MD5");
			md.update(secret);
			md.update(r, j - 16, 16);
			b = md.digest();
			for (int i = 0; i < 16;)
				r[j++] ^= b[i++];
		}
		return r;
	}

	void parseResponse(ByteBuffer r) throws Exception {
		int l = r.limit();
		if (l < 20)
			throw new IllegalArgumentException("response size less than 20, size=" + l);
		byte code = r.get();
		byte identifier = r.get();
		RequestContext rc = outstanding.get(identifier);
		if (rc == null)
			throw new IllegalArgumentException(
					"response identifier not found, may be duplicated response, identifier=" + identifier);
		int length = r.getShort();
		if (length != l)
			throw new IllegalArgumentException(
					"response wrong length, may be cheat, response.length=" + length + " but packet.length=" + l);
		byte[] authenticator = new byte[16];
		r.get(authenticator);
		MessageDigest md = MessageDigest.getInstance("MD5");
		r.rewind();
		r.limit(4);
		md.update(r);
		md.update(rc.authenticator);
		r.limit(l);
		r.position(20);
		md.update(r);
		md.update(secret);
		if (!Arrays.equals(authenticator, md.digest()))
			throw new IllegalArgumentException("response wrong authenticator, may be cheat, ignore.");
		rc.response(code == Access_Accept ? Result.Accept : Result.Reject);
	}

	@Override
	public synchronized void access(String username, String password, Consumer<Result> response) {
		if (stopped) {
			response.accept(Result.Fail);
			return;
		}
		dispatcher.execute(() -> {
			try {
				long start = System.currentTimeMillis();
				Byte identifier = slot.poll(timeout, TimeUnit.MILLISECONDS);
				if (identifier != null)
					sendRequest(
							new RequestContext(identifier, response, timeout - (System.currentTimeMillis() - start)),
							username, password);
				else
					response.accept(Result.Timeout);
			} catch (Exception e) {
				response.accept(Result.Fail);
				if (Trace.isDebugEnabled())
					Trace.debug("request exception", e);
			}
		}, null);
	}

	@Override
	public synchronized void stop() {
		if (stopped)
			return;
		stopped = true;
		dispatcher.await();
		try {
			channel.close();
		} catch (Exception e) {
		}
	}

	public Radius(ScheduledExecutorService scheduler, String host, int port, long timeout, String nasIdentifier,
			String secret) throws Exception {
		this.nasIdentifier = nasIdentifier.getBytes(StandardCharsets.UTF_8);
		this.secret = secret.getBytes(StandardCharsets.UTF_8);
		this.channel = DatagramChannel.open();
		this.channel.connect(new InetSocketAddress(host, port));
		for (int i = 0; i < 256; i++)
			slot.offer((byte) i);
		this.timeout = timeout;
		this.scheduler = scheduler;
		this.dispatcher = new Dispatcher(scheduler);
		this.scheduler.execute(() -> {
			ByteBuffer r = ByteBuffer.allocateDirect(4096).order(ByteOrder.BIG_ENDIAN);
			while (true) {
				try {
					r.clear();
					channel.read(r);
					r.flip();
					parseResponse(r);
				} catch (ClosedChannelException e) {
					new ArrayList<>(outstanding.values()).forEach(rc -> rc.response(Result.Fail));
					return;
				} catch (Exception e) {
					if (Trace.isDebugEnabled())
						Trace.debug("response exception", e);
				}
			}
		});
	}
}
