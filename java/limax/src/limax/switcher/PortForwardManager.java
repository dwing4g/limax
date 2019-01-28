package limax.switcher;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import limax.codec.CodecException;
import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.net.Manager;
import limax.net.ServerManager;
import limax.net.SizePolicyException;
import limax.net.Transport;
import limax.switcher.switcherendpoint.PortForward;
import limax.util.Trace;
import limax.util.transpond.FlowControlProcessor;
import limax.util.transpond.FlowControlTask;
import limax.util.transpond.Transpond;

final class PortForwardManager {

	private static final PortForwardManager instance = new PortForwardManager();

	private PortForwardManager() {
	}

	public static PortForwardManager getInstance() {
		return instance;
	}

	final static private int DefaultBufferSize = 1024 * 4;
	private final static Octets NULL = new Octets();

	private static final class Session implements FlowControlProcessor {
		private final Transport nio;
		private final int sid;
		private FlowControlTask task = null;
		private final AtomicBoolean needsendack = new AtomicBoolean(false);

		public Session(Transport nio, int sid) {
			this.nio = nio;
			this.sid = sid;
		}

		@Override
		public boolean startup(FlowControlTask task, SocketAddress local, SocketAddress peer) throws Exception {
			if (Trace.isDebugEnabled())
				Trace.debug("PortForwardManager.Session connected local = " + local + " peer = " + peer);
			this.task = task;
			new PortForward(PortForward.eConnect, sid, PortForward.eConnectV0, NULL).send(nio);
			return true;
		}

		@Override
		public void shutdown(boolean eventually) throws Exception {
			new PortForward(PortForward.eClose, sid,
					eventually ? PortForward.eCloseSessionClose : PortForward.eCloseSessionAbort, NULL).send(nio);
		}

		@Override
		public void sendDataTo(byte[] data) throws Exception {
			synchronized (task) {
				task.disableReceive();
			}
			new PortForward(PortForward.eForward, sid, PortForward.eForwardRaw, Octets.wrap(data)).send(nio);
		}

		@Override
		public void sendDone(long leftsize) throws Exception {
			if (leftsize > 0)
				return;
			if (needsendack.compareAndSet(true, false))
				new PortForward(PortForward.eForwardAck, sid, 0, NULL).send(nio);
		}

		private void onForward(Octets data) {
			needsendack.set(true);
			task.sendData(data.getByteBuffer());
		}

		private void onForwardAck() {
			synchronized (task) {
				task.enableReceive();
			}
		}

		@Override
		public int hashCode() {
			return Integer.hashCode(sid);
		}
	}

	private Map<Transport, Map<Integer, Session>> smap = new ConcurrentHashMap<>();

	final void onPortForward(PortForward p) throws Exception {
		switch (p.command) {
		case PortForward.eConnect:
			onConnect(p);
			break;
		case PortForward.eClose:
			onClose(p);
			break;
		case PortForward.eForward:
			onForward(p);
			break;
		case PortForward.eForwardAck:
			onForwardAck(p);
			break;
		default:
			if (Trace.isWarnEnabled())
				Trace.warn("PortForwardManager unknown command " + p);
			break;
		}
	}

	private static void sendClose(Manager manager, Transport nio, int sid, int code) {
		final PortForward send = new PortForward();
		send.command = PortForward.eClose;
		send.portsid = sid;
		send.code = code;
		try {
			send.send(nio);
		} catch (InstantiationException | SizePolicyException | CodecException e) {
		} finally {
			manager.close(nio);
		}
	}

	private static void sendClose(PortForward p, int code) {
		sendClose((ServerManager) p.getTransport().getManager(), p.getTransport(), p.portsid, code);
	}

	private void closeSession(Session ss) {
		final Map<Integer, Session> tm = smap.get(ss.nio);
		if (tm != null) {
			tm.remove(ss.sid);
			if (tm.isEmpty())
				smap.remove(ss.nio);
		}
	}

	private Session getSession(Transport nio, int sid) {
		final Map<Integer, Session> tm = smap.get(nio);
		return tm == null ? null : tm.get(sid);
	}

	private Session getSession(PortForward p) {
		return getSession(p.getTransport(), p.portsid);
	}

	private void onForwardAck(PortForward p) throws Exception {
		if (Trace.isDebugEnabled())
			Trace.debug("PortForwardManager onForwardAck " + p);
		final Session ss = getSession(p);
		if (null != ss)
			ss.onForwardAck();
	}

	private void onForward(PortForward p) {
		if (Trace.isDebugEnabled())
			Trace.debug("PortForwardManager onForward " + p);
		final Session ss = getSession(p);
		if (null != ss)
			ss.onForward(p.data);
	}

	private void onClose(PortForward p) {
		if (Trace.isDebugEnabled())
			Trace.debug("PortForwardManager onClose " + p);
		final Session ss = getSession(p);
		if (null != ss) {
			ss.task.closeSession();
			closeSession(ss);
		}
	}

	private void onConnect(PortForward p) {
		if (Trace.isDebugEnabled())
			Trace.debug("PortForwardManager onConnect " + p);

		if (PortForward.eConnectV0 != p.code) {
			sendClose(p, PortForward.eCloseUnknownConnectVersion);
			return;
		}

		if (null != getSession(p)) {
			sendClose(p, PortForward.eCloseConnectDuplicatePort);
			return;
		}

		final String serverIp;
		final int serverPort;
		final boolean asynchronous;

		try {
			final OctetsStream os = OctetsStream.wrap(p.data);
			serverIp = os.unmarshal_String();
			serverPort = os.unmarshal_int();
			asynchronous = os.unmarshal_boolean();
		} catch (MarshalException e) {
			if (Trace.isWarnEnabled())
				Trace.warn("onConnect", e);
			sendClose(p, PortForward.eCloseUnknownConnectVersion);
			return;
		}

		final Session session = new Session(p.getTransport(), p.portsid);
		smap.computeIfAbsent(p.getTransport(), k -> new ConcurrentHashMap<>()).put(p.portsid, session);
		try {
			Transpond.startConnectOnly(new InetSocketAddress(serverIp, serverPort), DefaultBufferSize, session,
					asynchronous);
		} catch (IOException e) {
			if (Trace.isWarnEnabled())
				Trace.warn("onConnect serverIp = " + serverIp + " serverPort = " + serverPort, e);
			sendClose(p, PortForward.eCloseSessionAbort);
			return;
		}
	}
}
