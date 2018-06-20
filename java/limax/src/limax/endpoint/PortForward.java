package limax.endpoint;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import limax.codec.CodecException;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.net.SizePolicyException;
import limax.net.io.NetModel;
import limax.net.io.NetTask;
import limax.net.io.ServerContext;
import limax.net.io.Transpond;
import limax.util.Trace;

public final class PortForward {

	private PortForward() {
	}

	public interface Listener {

		void onListenAccept(int sid, SocketAddress peerAddress);

		void onPortForwarSetup(int sid);

		void onPortForwarClose(int sid, int code, boolean byremote);

	}

	static public interface Manager {

		int startListen(final String serverIp, final int serverPort, final int localPort, final Listener listener)
				throws Exception;

		void stopListen(int lid) throws Exception;

		void closeSession(int sid) throws Exception;
	}

	final static class ManagerImpl implements Manager {

		final static private int DefaultBufferSize = 1024 * 4;

		private final Object mutex = new Object();
		private int sessionidgenerator = 0;

		private final Map<Integer, ServerContext> servers = new HashMap<Integer, ServerContext>();
		private final Map<Integer, Session> smap = new HashMap<Integer, Session>();
		private final EndpointManagerImpl manager;

		ManagerImpl(EndpointManagerImpl manager) {
			this.manager = manager;
		}

		private final class Session implements Transpond.FlowControlProcessor {

			private final String serverIp;
			private final int serverPort;
			private final int sessionid;
			private volatile Listener listener;
			private volatile boolean canrecv = false;
			private volatile boolean needsendack = false;

			private Transpond.FlowControlServerTask task = null;

			public Session(String serverIp, int serverPort, Listener listener) {
				this.serverIp = serverIp;
				this.serverPort = serverPort;
				this.listener = listener;

				synchronized (mutex) {
					sessionid = sessionidgenerator++;
					smap.put(sessionid, this);
				}
			}

			@Override
			public void shutdown(boolean eventually) throws CodecException {
				if (null != listener) {
					final int code = eventually ? limax.endpoint.switcherendpoint.PortForward.eCloseSessionClose
							: limax.endpoint.switcherendpoint.PortForward.eCloseSessionAbort;
					sendClose(code);
					listener.onPortForwarClose(sessionid, code, false);
					listener = null;
				}
			}

			@Override
			public boolean setup(SocketAddress local, SocketAddress peer) throws Exception {
				final limax.endpoint.switcherendpoint.PortForward send = new limax.endpoint.switcherendpoint.PortForward();
				send.command = limax.endpoint.switcherendpoint.PortForward.eConnect;
				send.portsid = sessionid;
				send.code = limax.endpoint.switcherendpoint.PortForward.eConnectV0;
				send.data = new OctetsStream().marshal(serverIp).marshal(serverPort);
				send.send(manager.manager.getTransport());
				if (null != listener)
					listener.onListenAccept(sessionid, peer);
				return false;
			}

			@Override
			public void sendDataTo(byte[] data) throws CodecException {
				Trace.info("endpoint PortForward send sendForward canrecv = " + canrecv);
				canrecv = false;
				if (Trace.isDebugEnabled())
					Trace.debug("endpoint PortForward send sendForward canrecv = " + canrecv);

				final limax.endpoint.switcherendpoint.PortForward send = new limax.endpoint.switcherendpoint.PortForward();
				send.command = limax.endpoint.switcherendpoint.PortForward.eForward;
				send.portsid = sessionid;
				send.code = limax.endpoint.switcherendpoint.PortForward.eForwardRaw;
				send.data.swap(Octets.wrap(data));
				try {
					send.send(manager.manager.getTransport());
				} catch (InstantiationException e) {
				} catch (ClassCastException e) {
				} catch (SizePolicyException e) {
				}
			}

			@Override
			public boolean isCanRecv() {
				return canrecv;
			}

			@Override
			public void sendDone() throws CodecException {
				if (needsendack) {
					needsendack = false;
					if (Trace.isDebugEnabled())
						Trace.debug("endpoint PortForward send eForwardAck");

					final limax.endpoint.switcherendpoint.PortForward send = new limax.endpoint.switcherendpoint.PortForward();
					send.command = limax.endpoint.switcherendpoint.PortForward.eForwardAck;
					send.portsid = sessionid;
					send.code = 0;
					try {
						send.send(manager.manager.getTransport());
					} catch (InstantiationException e) {
					} catch (ClassCastException e) {
					} catch (SizePolicyException e) {
					}
				}
			}

			private void sendClose(int code) throws CodecException {
				sendPortForwardClose(sessionid, code);
			}

			private void onProtocol(limax.endpoint.switcherendpoint.PortForward p)
					throws InstantiationException, SizePolicyException, CodecException {
				switch (p.command) {
				case limax.endpoint.switcherendpoint.PortForward.eConnect:
					if (Trace.isDebugEnabled())
						Trace.debug("endpoint PortForward connect done!");
					canrecv = true;
					task.readyNow();
					if (null != listener)
						listener.onPortForwarSetup(sessionid);
					return;
				case limax.endpoint.switcherendpoint.PortForward.eClose:
					if (Trace.isInfoEnabled())
						Trace.info("endpoint PortForward close code = " + p.code + "!");
					if (null != listener) {
						listener.onPortForwarClose(sessionid, p.code, true);
						listener = null;
					}
					task.closeSession();
					closeSession(this);
					return;
				case limax.endpoint.switcherendpoint.PortForward.eForward:
					if (limax.endpoint.switcherendpoint.PortForward.eForwardRaw != p.code) {
						sendClose(limax.endpoint.switcherendpoint.PortForward.eCloseUnknownForwardType);
					} else {
						if (Trace.isDebugEnabled())
							Trace.debug("endpoint PortForward recv eForward");
						needsendack = true;
						task.sendData(p.data.getByteBuffer());
					}
					return;
				case limax.endpoint.switcherendpoint.PortForward.eForwardAck:
					synchronized (task) {
						canrecv = true;
						if (Trace.isDebugEnabled())
							Trace.debug("endpoint PortForward recv eForwardAck canrecv = " + canrecv);
						task.checkRecvMoreData();
					}
					return;
				}
			}

		}

		@Override
		public int startListen(final String serverIp, final int serverPort, final int localPort,
				final Listener listener) throws Exception {
			final SocketAddress localAddress = new InetSocketAddress(localPort);
			final ServerContext sc;
			sc = NetModel.addServer(localAddress, 0, DefaultBufferSize, DefaultBufferSize,
					new ServerContext.NetTaskConstructor() {
						@Override
						public NetTask newInstance(ServerContext context) {
							final Session session = new Session(serverIp, serverPort, listener);
							session.task = Transpond.createFlowControlServerTask(DefaultBufferSize, DefaultBufferSize,
									session);
							session.task.setTaskCounter(context);
							return session.task.getNetTask();
						}

						@Override
						public String getServiceName() {
							return "PortForward.Server";
						}
					}, true);

			synchronized (mutex) {
				final int id = sessionidgenerator++;
				servers.put(id, sc);
				return id;
			}
		}

		@Override
		public void stopListen(int id) throws Exception {
			final ServerContext sc;
			synchronized (mutex) {
				sc = servers.get(id);
			}
			if (null != sc)
				sc.close();
		}

		@Override
		public void closeSession(int sid) throws Exception {
			final Session session;
			synchronized (mutex) {
				session = smap.remove(sid);
			}
			if (null != session)
				session.sendClose(limax.endpoint.switcherendpoint.PortForward.eCloseManualClosed);
		}

		void shutdown() {
			final List<ServerContext> scs = new LinkedList<ServerContext>();
			synchronized (mutex) {
				scs.addAll(servers.values());
				servers.clear();
			}
			for (final ServerContext sc : scs) {
				try {
					sc.close();
				} catch (IOException e) {
					if (Trace.isWarnEnabled())
						Trace.warn("PortForward stopListen", e);
				}
			}

			final List<Session> ss = new LinkedList<Session>();
			synchronized (mutex) {
				ss.addAll(smap.values());
				smap.clear();
			}
			for (final Session s : ss)
				s.task.closeSession();
		}

		private void sendPortForwardClose(int sid, int code) throws CodecException {
			final limax.endpoint.switcherendpoint.PortForward send = new limax.endpoint.switcherendpoint.PortForward();
			send.command = limax.endpoint.switcherendpoint.PortForward.eClose;
			send.portsid = sid;
			send.code = code;
			try {
				send.send(manager.manager.getTransport());
			} catch (InstantiationException e) {
			} catch (ClassCastException e) {
			} catch (SizePolicyException e) {
			}
		}

		void onProtocol(limax.endpoint.switcherendpoint.PortForward p)
				throws InstantiationException, SizePolicyException, CodecException {
			final Session ss;
			synchronized (mutex) {
				ss = smap.get(p.portsid);
			}
			if (null == ss)
				sendPortForwardClose(p.portsid, limax.endpoint.switcherendpoint.PortForward.eCloseForwardPortNotFound);
			else
				ss.onProtocol(p);
		}

		private void closeSession(Session ss) {
			synchronized (mutex) {
				smap.remove(ss.sessionid);
			}
		}

	}

	static public Manager getManager(EndpointManager manager) {
		if (null == manager.getTransport())
			return null;
		return ((EndpointManagerImpl) manager).getPortForwardManager();
	}
}
