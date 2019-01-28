package limax.endpoint;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.net.io.ServerContext;
import limax.util.Trace;
import limax.util.transpond.FlowControlProcessor;
import limax.util.transpond.FlowControlTask;
import limax.util.transpond.Transpond;

public final class PortForward {

	private PortForward() {
	}

	public interface Listener {

		void onListenAccept(int sid, SocketAddress peerAddress);

		void onPortForwardSetup(int sid);

		void onPortForwardClose(int sid, int code, boolean byremote);

	}

	public static interface Manager {

		int startListen(String serverIp, int serverPort, int localPort, Listener listener, boolean asynchronous)
				throws Exception;

		void stopListen(int lid) throws Exception;

		void closeSession(int sid) throws Exception;

		void shutdown();
	}

	static final class ManagerImpl implements Manager {

		final static private int DefaultBufferSize = 1024 * 4;

		private AtomicInteger sessionidgenerator = new AtomicInteger();
		private final Map<Integer, ServerContext> servers = new ConcurrentHashMap<Integer, ServerContext>();
		private final Map<Integer, Session> smap = new ConcurrentHashMap<Integer, Session>();
		private final EndpointManagerImpl manager;

		ManagerImpl(EndpointManagerImpl manager) {
			this.manager = manager;
		}

		private final class Session implements FlowControlProcessor {

			private final String serverIp;
			private final int serverPort;
			private final int sessionid;
			private final boolean asynchronous;
			private Listener listener;
			private final AtomicBoolean needsendack = new AtomicBoolean(false);

			private FlowControlTask task = null;

			public Session(String serverIp, int serverPort, Listener listener, boolean asynchronous) {
				this.serverIp = serverIp;
				this.serverPort = serverPort;
				this.listener = listener;
				this.asynchronous = asynchronous;

				sessionid = sessionidgenerator.incrementAndGet();
				smap.put(sessionid, this);
			}

			@Override
			public void shutdown(boolean eventually) throws Exception {
				final int code = eventually ? limax.endpoint.switcherendpoint.PortForward.eCloseSessionClose
						: limax.endpoint.switcherendpoint.PortForward.eCloseSessionAbort;
				sendClose(code);
				if (null != listener) {
					listener.onPortForwardClose(sessionid, code, false);
					listener = null;
				}
			}

			@Override
			public boolean startup(FlowControlTask task, SocketAddress local, SocketAddress peer) throws Exception {
				this.task = task;
				final limax.endpoint.switcherendpoint.PortForward send = new limax.endpoint.switcherendpoint.PortForward();
				send.command = limax.endpoint.switcherendpoint.PortForward.eConnect;
				send.portsid = sessionid;
				send.code = limax.endpoint.switcherendpoint.PortForward.eConnectV0;
				send.data = new OctetsStream().marshal(serverIp).marshal(serverPort).marshal(asynchronous);
				send.send(manager.getTransport());
				if (null != listener)
					listener.onListenAccept(sessionid, peer);
				return false;
			}

			@Override
			public void sendDataTo(byte[] data) throws Exception {
				synchronized (task) {
					task.disableReceive();
				}

				final limax.endpoint.switcherendpoint.PortForward send = new limax.endpoint.switcherendpoint.PortForward();
				send.command = limax.endpoint.switcherendpoint.PortForward.eForward;
				send.portsid = sessionid;
				send.code = limax.endpoint.switcherendpoint.PortForward.eForwardRaw;
				send.data.swap(Octets.wrap(data));
				send.send(manager.getTransport());
			}

			@Override
			public void sendDone(long leftsize) throws Exception {
				if (leftsize > 0)
					return;
				if (needsendack.compareAndSet(true, false)) {
					final limax.endpoint.switcherendpoint.PortForward send = new limax.endpoint.switcherendpoint.PortForward();
					send.command = limax.endpoint.switcherendpoint.PortForward.eForwardAck;
					send.portsid = sessionid;
					send.code = 0;
					send.send(manager.getTransport());
				}
			}

			private void sendClose(int code) throws Exception {
				sendPortForwardClose(sessionid, code);
			}

			private void onProtocol(limax.endpoint.switcherendpoint.PortForward p) throws Exception {
				switch (p.command) {
				case limax.endpoint.switcherendpoint.PortForward.eConnect:
					task.enableReceive();
					needsendack.set(false);
					if (null != listener)
						listener.onPortForwardSetup(sessionid);
					return;
				case limax.endpoint.switcherendpoint.PortForward.eClose:
					if (null != listener) {
						listener.onPortForwardClose(sessionid, p.code, true);
						listener = null;
					}
					task.closeSession();
					closeSession(this);
					return;
				case limax.endpoint.switcherendpoint.PortForward.eForward:
					if (limax.endpoint.switcherendpoint.PortForward.eForwardRaw != p.code) {
						sendClose(limax.endpoint.switcherendpoint.PortForward.eCloseUnknownForwardType);
					} else {
						needsendack.set(true);
						task.sendData(p.data.getByteBuffer());
					}
					return;
				case limax.endpoint.switcherendpoint.PortForward.eForwardAck:
					synchronized (task) {
						task.enableReceive();
					}
					return;
				}
			}

			@Override
			public int hashCode() {
				return Integer.hashCode(sessionid);
			}
		}

		@Override
		public int startListen(final String serverIp, final int serverPort, final int localPort,
				final Listener listener, boolean asynchronous) throws Exception {
			final SocketAddress localAddress = new InetSocketAddress(localPort);
			final ServerContext sc = Transpond.startListenOnly(localAddress, DefaultBufferSize, "PortForward.Server",
					() -> new Session(serverIp, serverPort, listener, asynchronous), asynchronous);
			sc.open();
			final int id = sessionidgenerator.incrementAndGet();
			servers.put(id, sc);
			return id;
		}

		@Override
		public void stopListen(int id) throws Exception {
			final ServerContext sc = servers.remove(id);
			if (null != sc)
				sc.close();
		}

		@Override
		public void closeSession(int sid) throws Exception {
			final Session session = smap.remove(sid);
			if (null != session)
				session.sendClose(limax.endpoint.switcherendpoint.PortForward.eCloseManualClosed);
		}

		@Override
		public void shutdown() {
			final List<ServerContext> scs = new LinkedList<ServerContext>();
			scs.addAll(servers.values());
			servers.clear();

			for (final ServerContext sc : scs) {
				try {
					sc.close();
				} catch (IOException e) {
					if (Trace.isWarnEnabled())
						Trace.warn("PortForward stopListen", e);
				}
			}

			final List<Session> ss = new LinkedList<Session>();
			ss.addAll(smap.values());
			smap.clear();
			for (final Session s : ss)
				s.task.closeSession();
		}

		private void sendPortForwardClose(int sid, int code) throws Exception {
			final limax.endpoint.switcherendpoint.PortForward send = new limax.endpoint.switcherendpoint.PortForward();
			send.command = limax.endpoint.switcherendpoint.PortForward.eClose;
			send.portsid = sid;
			send.code = code;
			try {
				send.send(manager.getTransport());
			} catch (Exception e) {
			}
		}

		void onProtocol(limax.endpoint.switcherendpoint.PortForward p) throws Exception {
			Session ss = smap.get(p.portsid);
			if (null == ss)
				sendPortForwardClose(p.portsid, limax.endpoint.switcherendpoint.PortForward.eCloseForwardPortNotFound);
			else
				ss.onProtocol(p);
		}

		private void closeSession(Session ss) {
			smap.remove(ss.sessionid);
		}
	}

	public static Manager getManager(EndpointManager manager) {
		return manager.getTransport() == null ? null : ((EndpointManagerImpl) manager).getPortForwardManager();
	}
}
