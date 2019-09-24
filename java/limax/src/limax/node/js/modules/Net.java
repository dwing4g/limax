package limax.node.js.modules;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.script.Invocable;

import limax.node.js.Buffer;
import limax.node.js.EventLoop;
import limax.node.js.EventLoop.Callback;
import limax.node.js.EventLoop.EventObject;
import limax.node.js.Module;
import limax.node.js.modules.tls.TLSConfig;
import limax.node.js.modules.tls.TLSServerEngine;
import limax.util.HashExecutor;
import limax.util.Pair;

public final class Net implements Module {
	private final static HashExecutor hashExecutor = EventLoop
			.createHashExecutor(Integer.getInteger("limax.node.js.module.Net.TLSExchange.concurrency", 32));
	private final EventLoop eventLoop;
	private final Invocable invocable;
	private final Callback NULLCB;

	@FunctionalInterface
	private interface RunnableWithException {
		void run() throws Exception;
	}

	private static InetSocketAddress createSocketAddress(String host, int port, boolean server) {
		if (host != null)
			return new InetSocketAddress(host, port);
		if (server)
			new InetSocketAddress(port);
		return new InetSocketAddress("localhost", port);
	}

	public Net(EventLoop eventLoop) {
		this.eventLoop = eventLoop;
		this.invocable = eventLoop.getInvocable();
		this.NULLCB = eventLoop.createCallback(null);
	}

	private class TLS {
		private final SSLContext ctx;
		private final boolean needClientAuth;
		private final List<SNIServerName> serverNames;

		TLS(Object callback) throws Exception {
			TLSConfig config = new TLSConfig(invocable);
			eventLoop.getInvocable().invokeMethod(callback, "call", null, config);
			this.needClientAuth = config.getNeedClientAuth();
			this.serverNames = config.getServerNames();
			this.ctx = SSLContext.getInstance(config.getProtocol());
			this.ctx.init(config.getKeyManagers(), config.getTrustManagers(), null);
		}

		SSLEngine createEngine() {
			SSLEngine engine = new TLSServerEngine(ctx.createSSLEngine());
			engine.setUseClientMode(false);
			engine.setNeedClientAuth(needClientAuth);
			return engine;
		}

		SSLEngine createEngine(String peerHost, int peerPort) {
			SSLEngine engine = ctx.createSSLEngine(peerHost, peerPort);
			engine.setUseClientMode(true);
			if (!serverNames.isEmpty()) {
				SSLParameters parameters = engine.getSSLParameters();
				parameters.setServerNames(serverNames);
				engine.setSSLParameters(parameters);
			}
			return engine;
		}
	}

	private class TLSExchange {
		private final SSLEngine engine;
		private final Socket socket;
		private final Callback callbackDown;
		private final Callback callbackPeerInfo;
		private ByteBuffer netDataIn;
		private ByteBuffer netDataOut;
		private final Deque<ByteBuffer> appDataIn = new ArrayDeque<>();
		private final Deque<Pair<Deque<ByteBuffer>, Callback>> appDataOut = new ArrayDeque<>();
		private volatile boolean renegotiate;
		private boolean shuttingdown = false;

		TLSExchange(SSLEngine engine, Socket socket, Callback callbackDown, Callback callbackPeerInfo) {
			this.engine = engine;
			this.socket = socket;
			this.callbackDown = callbackDown;
			this.callbackPeerInfo = callbackPeerInfo;
			this.netDataIn = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
			this.netDataOut = ByteBuffer.allocateDirect(engine.getSession().getPacketBufferSize() * 2);
			appDataIn.add(ByteBuffer.allocate(engine.getSession().getApplicationBufferSize()));
		}

		private SSLEngineResult wrap() throws SSLException {
			Pair<Deque<ByteBuffer>, Callback> pair = appDataOut.peek();
			Deque<ByteBuffer> data = pair == null ? new ArrayDeque<>() : pair.getKey();
			while (true) {
				SSLEngineResult rs = engine.wrap(data.toArray(new ByteBuffer[0]), netDataOut);
				if (netDataOut.position() > 0) {
					while (!data.isEmpty() && !data.peek().hasRemaining())
						data.poll();
					ByteBuffer tmp = netDataOut;
					netDataOut = tmp.slice();
					tmp.flip();
					socket.write(tmp, pair != null && data.isEmpty() ? appDataOut.poll().getValue() : NULLCB);
				}
				switch (rs.getStatus()) {
				case BUFFER_OVERFLOW:
					netDataOut = ByteBuffer.allocateDirect(engine.getSession().getPacketBufferSize() * 2);
					break;
				default:
					return rs;
				}
			}
		}

		private SSLEngineResult unwrap() throws SSLException {
			netDataIn.flip();
			while (true) {
				SSLEngineResult rs = engine.unwrap(netDataIn, appDataIn.getLast());
				switch (rs.getStatus()) {
				case BUFFER_OVERFLOW:
					appDataIn.add(ByteBuffer.allocate(engine.getSession().getApplicationBufferSize()));
					break;
				case BUFFER_UNDERFLOW:
					if (netDataIn.capacity() < engine.getSession().getPacketBufferSize())
						netDataIn = ByteBuffer.allocate(engine.getSession().getPacketBufferSize()).put(netDataIn);
				default:
					netDataIn.compact();
					return rs;
				}
			}
		}

		private boolean processResult(SSLEngineResult rs) throws SSLException {
			switch (rs.getHandshakeStatus()) {
			case NEED_TASK:
				for (Runnable task; (task = engine.getDelegatedTask()) != null;) {
					Runnable runnable = task;
					eventLoop.execute(() -> {
						runnable.run();
						socket.execute(() -> {
							while (engine.getHandshakeStatus() == HandshakeStatus.NEED_WRAP && processResult(wrap())
									|| engine.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP
											&& processResult(unwrap()))
								;
						}, callbackDown);
					});
				}
				return false;
			case NEED_WRAP:
				rs = wrap();
				break;
			case NEED_UNWRAP:
				rs = unwrap();
			default:
			}
			switch (rs.getHandshakeStatus()) {
			case FINISHED:
				if (shuttingdown) {
					shuttingdown = false;
					for (engine.closeOutbound(); !engine.isOutboundDone();)
						wrap();
				} else {
					SSLSession session = engine.getSession();
					try {
						callbackPeerInfo.call(Stream.concat(Stream.of(session.getPeerPrincipal()),
								Arrays.stream(session.getPeerCertificates())).toArray());
					} catch (Exception e) {
						callbackPeerInfo.call(new Object[] {});
					}
				}
			case NOT_HANDSHAKING:
				if (rs.getStatus() != Status.CLOSED)
					while (!appDataOut.isEmpty())
						wrap();
			default:
			}
			if (rs.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING) {
				if (rs.getStatus() == Status.CLOSED) {
					if (netDataIn.position() > 0) {
						netDataIn.flip();
						appDataIn.add(netDataIn);
					}
					while (!appDataOut.isEmpty()) {
						Pair<Deque<ByteBuffer>, Callback> pair = appDataOut.poll();
						socket.writev(pair.getKey().toArray(new ByteBuffer[0]), pair.getValue());
					}
					socket.tls.set(null);
					callbackDown.call(new Object[] {});
				} else if (renegotiate) {
					engine.getSession().invalidate();
					engine.beginHandshake();
					renegotiate = false;
				}
			}
			return rs.getStatus() == Status.OK;
		}

		void read(ByteBuffer in, Callback cb, Runnable failaction) {
			socket.execute(() -> {
				int len = netDataIn.position() + in.remaining();
				if (len > netDataIn.capacity()) {
					netDataIn.flip();
					netDataIn = ByteBuffer.allocate(len).put(netDataIn);
				}
				netDataIn.put(in);
				while (processResult(unwrap()))
					;
				if (appDataIn.size() > 1) {
					ByteBuffer r = ByteBuffer.allocate(appDataIn.stream().mapToInt(bb -> bb.position()).sum());
					appDataIn.forEach(bb -> {
						bb.flip();
						r.put(bb);
					});
					r.flip();
					cb.call(null, len, new Buffer(r));
					ByteBuffer bb = appDataIn.getLast();
					bb.clear();
					appDataIn.clear();
					appDataIn.add(bb);
					return;
				}
				ByteBuffer r = appDataIn.peekFirst();
				if (r.position() > 0) {
					r.flip();
					cb.call(null, len, new Buffer(r.slice()));
					appDataIn.remove();
					appDataIn.add(r.compact().slice());
					return;
				}
				failaction.run();
			}, cb);
		}

		void writev(ByteBuffer[] bbs, Callback cb) {
			socket.execute(() -> {
				appDataOut.add(new Pair<>(Arrays.stream(bbs).collect(ArrayDeque::new, Deque::add, Deque::addAll), cb));
				processResult(wrap());
			}, cb);
		}

		void shutdown() {
			socket.execute(() -> {
				shuttingdown = true;
				if (engine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING)
					processResult(new SSLEngineResult(Status.CLOSED, HandshakeStatus.FINISHED, 0, 0));
			}, callbackDown);
		}

		void tlsrenegotiate() {
			renegotiate = true;
		}
	}

	private static class ServerManager {
		private final static Map<SocketAddress, ServerManager> managers = new ConcurrentHashMap<>();
		private final List<Server> servers = new ArrayList<>();
		private int round = 0;

		private final AsynchronousServerSocketChannel assc;

		private synchronized boolean completed(AsynchronousSocketChannel asc) {
			int count = servers.size();
			if (count == 0)
				return false;
			for (int i = 0; i < count; i++) {
				if (servers.get(round = (round + 1) % count).completed(asc))
					return true;
			}
			try {
				asc.close();
			} catch (Exception e) {
			}
			return true;
		}

		private synchronized boolean failed(Throwable exc) {
			int count = servers.size();
			if (count == 0)
				return false;
			servers.get(round = (round + 1) % servers.size()).failed(exc);
			return true;
		}

		private synchronized void addServer(Server server) {
			servers.add(server);
		}

		private synchronized boolean removeServer(Server server) {
			servers.remove(server);
			if (!servers.isEmpty())
				return false;
			try {
				assc.close();
			} catch (Exception e) {
			}
			return true;
		}

		private ServerManager(SocketAddress sa, int backlog) throws Exception {
			assc = AsynchronousServerSocketChannel.open();
			assc.setOption(StandardSocketOptions.SO_REUSEADDR, true);
			assc.bind(sa, backlog);
			accept();
		}

		private void accept() {
			try {
				assc.accept(null, new CompletionHandler<AsynchronousSocketChannel, Object>() {

					@Override
					public void completed(AsynchronousSocketChannel asc, Object attachment) {
						if (ServerManager.this.completed(asc))
							accept();
					}

					@Override
					public void failed(Throwable exc, Object attachment) {
						if (ServerManager.this.failed(exc))
							accept();
					}
				});
			} catch (Exception e) {
			}
		}

		static synchronized void listen(Server server, int backlog) throws Exception {
			ServerManager sm = managers.get(server.sa);
			if (sm == null)
				managers.put(server.sa, sm = new ServerManager(server.sa, backlog));
			sm.addServer(server);
		}

		static synchronized void close(Server server) {
			ServerManager sm = managers.get(server.sa);
			if (sm != null && sm.removeServer(server))
				managers.remove(server.sa);
		}
	}

	public class Server {
		private final Set<AsynchronousSocketChannel> channels = Collections.newSetFromMap(new ConcurrentHashMap<>());
		private final Object THIS;
		private int maxConnections = Integer.MAX_VALUE;
		private boolean closed = false;
		private Callback cb;
		private EventObject evo;
		private InetSocketAddress sa;

		Server(Object THIS) throws Exception {
			this.THIS = THIS;
		}

		public Object[] address() {
			try {
				InetAddress addr = sa.getAddress();
				return new Object[] { sa.getPort(), addr instanceof Inet4Address ? "IPv4" : "IPv6",
						addr.getHostAddress() };
			} catch (Exception e) {
				return new Object[] {};
			}
		}

		boolean completed(AsynchronousSocketChannel asc) {
			if (channels.size() >= maxConnections)
				return false;
			channels.add(asc);
			cb.call(null, asc);
			return true;
		}

		void failed(Throwable exc) {
			cb.call(exc);
		}

		public void listen(int port, String host, int backlog, Object callback) throws Exception {
			if (evo != null)
				cb.call(new Exception("Duplicate listen is forbidden"));
			this.evo = eventLoop.createEventObject();
			this.cb = eventLoop.createCallback(callback);
			this.sa = createSocketAddress(host, port, true);
			ServerManager.listen(this, backlog);
			invocable.invokeMethod(THIS, "emit", "listening");
		}

		private void gracefulClose() {
			if (channels.isEmpty()) {
				try {
					invocable.invokeMethod(THIS, "emit", "close");
				} catch (Exception e) {
				} finally {
					evo.queue();
				}
			}
		}

		public void close() {
			ServerManager.close(this);
			closed = true;
			gracefulClose();
		}

		void detach(AsynchronousSocketChannel sc) {
			channels.remove(sc);
			if (closed)
				gracefulClose();
		}

		public void getConnections(Object callback) {
			eventLoop.execute(callback, r -> r.add(channels.size()));
		}

		public boolean isListening() {
			return evo != null;
		}

		public int getMaxConnections() {
			return maxConnections;
		}

		public void setMaxConnections(int maxConnections) {
			this.maxConnections = maxConnections;
		}

		public void ref() {
			evo.ref();
		}

		public void unref() {
			evo.unref();
		}
	}

	public class Socket {
		private final AsynchronousSocketChannel asc;
		private final Queue<ByteBuffer> wqueue = new ArrayDeque<>();
		private final Queue<Callback> wcallback = new ArrayDeque<>();
		private boolean writting = false;
		private final EventObject evo;
		private final Server server;
		private final String host;
		private final int port;
		private final AtomicReference<TLSExchange> tls = new AtomicReference<>();
		private long timeout = Integer.MAX_VALUE;

		Socket(AsynchronousSocketChannel asc, EventObject evo, Server server, String host, int port) {
			this.asc = asc;
			this.evo = evo;
			this.server = server;
			this.host = host;
			this.port = port;
		}

		Socket(AsynchronousSocketChannel asc, Server owner) {
			this(asc, eventLoop.createEventObject(), owner, null, 0);
		}

		Socket(AsynchronousSocketChannel asc, EventObject evo, String host, int port) {
			this(asc, evo, null, host, port);
		}

		public Object[] address() {
			try {
				InetSocketAddress sa = (InetSocketAddress) asc.getLocalAddress();
				InetAddress addr = sa.getAddress();
				return new Object[] { sa.getPort(), addr instanceof Inet4Address ? "IPv4" : "IPv6",
						addr.getHostAddress() };
			} catch (Exception e) {
				return new Object[] {};
			}
		}

		public void destroy() {
			try {
				asc.shutdownOutput(); // send FIN avoid RST on Windows
				asc.close();
				if (server != null)
					server.detach(asc);
			} catch (Exception e) {
			} finally {
				evo.queue();
			}
		}

		public String getLocalAddress() {
			try {
				return ((InetSocketAddress) asc.getLocalAddress()).getAddress().getHostAddress();
			} catch (Exception e) {
				return null;
			}
		}

		public Integer getLocalPort() {
			try {
				return ((InetSocketAddress) asc.getLocalAddress()).getPort();
			} catch (Exception e) {
				return null;
			}
		}

		public String getRemoteAddress() {
			try {
				return ((InetSocketAddress) asc.getRemoteAddress()).getAddress().getHostAddress();
			} catch (Exception e) {
				return null;
			}
		}

		public String getRemoteFamily() {
			try {
				return ((InetSocketAddress) asc.getRemoteAddress()).getAddress() instanceof Inet4Address ? "IPv4"
						: "IPv6";
			} catch (IOException e) {
				return null;
			}
		}

		public Integer getRemotePort() {
			try {
				return ((InetSocketAddress) asc.getRemoteAddress()).getPort();
			} catch (Exception e) {
				return null;
			}
		}

		public void setKeepAlive(boolean on) {
			try {
				asc.setOption(StandardSocketOptions.SO_KEEPALIVE, on);
			} catch (Exception e) {
			}
		}

		public void setNoDelay(boolean on) {
			try {
				asc.setOption(StandardSocketOptions.TCP_NODELAY, on);
			} catch (Exception e) {
			}
		}

		public void setTimeout(int timeout) {
			this.timeout = timeout;
		}

		public void read(Integer size, Object callback) {
			ByteBuffer bb = ByteBuffer.allocate(size == null ? 16384 : size);
			Callback cb = eventLoop.createCallback(callback);
			try {
				asc.read(bb, timeout, TimeUnit.MILLISECONDS, null, new CompletionHandler<Integer, Object>() {
					@Override
					public void completed(Integer result, Object attachment) {
						TLSExchange tls = Socket.this.tls.get();
						bb.flip();
						if (tls == null || result == -1)
							cb.call(null, result, new Buffer(bb));
						else
							tls.read(bb, cb, () -> read(size, callback));
					}

					@Override
					public void failed(Throwable exc, Object attachment) {
						cb.call(exc);
					}
				});
			} catch (Exception e) {
				cb.call(e);
			}
		}

		private synchronized void writedone() {
			writting = false;
			while (!wqueue.isEmpty() && !wqueue.peek().hasRemaining()) {
				wqueue.poll();
				Callback cb = wcallback.poll();
				if (cb != NULLCB)
					cb.call(new Object[] {});
			}
			if (!wqueue.isEmpty())
				writeloop();
		}

		private synchronized void writefail(Throwable exc) {
			writting = false;
			wcallback.stream().filter(Objects::nonNull).forEach(cb -> cb.call(exc));
			wqueue.clear();
			wcallback.clear();
		}

		private void write(ByteBuffer[] srcs) {
			try {
				asc.write(srcs, 0, srcs.length, timeout, TimeUnit.MILLISECONDS, null,
						new CompletionHandler<Long, Object>() {

							@Override
							public void completed(Long result, Object attachment) {
								writedone();
							}

							@Override
							public void failed(Throwable exc, Object attachment) {
								writefail(exc);
							}
						});
			} catch (Exception e) {
				writefail(e);
			}
		}

		private void writeloop() {
			if (writting)
				return;
			writting = true;
			write(wqueue.toArray(new ByteBuffer[0]));
		}

		synchronized void write(ByteBuffer bb, Callback cb) {
			wqueue.add(bb);
			wcallback.add(cb);
			writeloop();
		}

		synchronized void writev(ByteBuffer[] bbs, Callback cb) {
			int len = bbs.length;
			for (int i = 0; i < len; i++)
				wqueue.add(bbs[i]);
			while (--len > 0)
				wcallback.add(NULLCB);
			wcallback.add(cb);
			writeloop();
		}

		public void write(Buffer buffer, Object callback) {
			writev(new Object[] { buffer }, callback);
		}

		public void writev(Object[] srcs, Object callback) {
			Callback cb = eventLoop.createCallback(callback);
			int bytes = 0;
			int len = srcs.length;
			ByteBuffer[] bbs = new ByteBuffer[len];
			for (int i = 0; i < len; i++)
				bytes += (bbs[i] = ((Buffer) srcs[i]).toByteBuffer()).remaining();
			if (bytes == 0) {
				cb.call(null, 0);
				return;
			}
			TLSExchange tls = this.tls.get();
			if (tls == null)
				writev(bbs, cb);
			else
				tls.writev(bbs, cb);
		}

		void execute(RunnableWithException r, Callback cb) {
			hashExecutor.execute(this, () -> {
				try {
					r.run();
				} catch (Exception e) {
					cb.call(e);
				}
			});
		}

		public void shutdownInput() {
			try {
				asc.shutdownInput();
			} catch (Exception e) {
			}
		}

		public void shutdownOutput() {
			try {
				asc.shutdownOutput();
			} catch (Exception e) {
			}
		}

		public void starttls(TLS tls, Object callbackDown, Object callbackPeerInfo) throws Exception {
			this.tls.compareAndSet(null,
					new TLSExchange(server != null ? tls.createEngine() : tls.createEngine(host, port), this,
							eventLoop.createCallback(callbackDown), eventLoop.createCallback(callbackPeerInfo)));
		}

		public void stoptls() {
			TLSExchange tls = this.tls.get();
			if (tls != null)
				tls.shutdown();
		}

		public void tlsrenegotiate() {
			TLSExchange tls = this.tls.get();
			if (tls != null)
				tls.tlsrenegotiate();
		}

		public void ref() {
			evo.ref();
		}

		public void unref() {
			evo.unref();
		}
	}

	public Socket createSocket(AsynchronousSocketChannel sc, Server server) {
		return new Socket(sc, server);
	}

	public void connect(int port, String host, String localAddress, Integer localPort, Object callback) {
		AsynchronousSocketChannel asc;
		Callback cb = eventLoop.createCallback(callback);
		try {
			asc = AsynchronousSocketChannel.open();
			if (localAddress != null || localPort != null)
				asc.bind(createSocketAddress(localAddress, localPort == null ? 0 : localPort, true));
			EventObject evo = eventLoop.createEventObject();
			asc.connect(createSocketAddress(host, port, false), null, new CompletionHandler<Void, Object>() {

				@Override
				public void completed(Void result, Object attachment) {
					cb.call(null, new Socket(asc, evo, host, port));
				}

				@Override
				public void failed(Throwable exc, Object attachment) {
					cb.call(exc);
					evo.queue();
				}
			});
		} catch (Exception e) {
			cb.call(e);
		}
	}

	public Server createServer(Object THIS) throws Exception {
		return new Server(THIS);
	}

	public TLS createTLS(Object callback) throws Exception {
		return new TLS(callback);
	}
}
