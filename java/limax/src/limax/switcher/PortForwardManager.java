package limax.switcher;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

import limax.codec.CodecException;
import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.net.Manager;
import limax.net.ServerManager;
import limax.net.SizePolicyException;
import limax.net.Transport;
import limax.net.io.NetModel;
import limax.net.io.Transpond;
import limax.switcher.switcherendpoint.PortForward;
import limax.util.Trace;

class PortForwardManager {

	private static final PortForwardManager instance = new PortForwardManager();

	private PortForwardManager() {
	}

	public static PortForwardManager getInstance() {
		return instance;
	}

	final static private int DefaultBufferSize = 1024 * 4;

	static private final class Session implements Transpond.FlowControlProcessor {
		private volatile boolean canrecv = false;
		private volatile boolean needsendack = false;
		private final Transport nio;
		private final int sid;
		private Transpond.FlowControlClientTask task = null;

		public Session(Transport nio, int sid) {
			this.nio = nio;
			this.sid = sid;
		}

		@Override
		public boolean setup(SocketAddress local, SocketAddress peer) throws Exception {
			if (Trace.isDebugEnabled())
				Trace.debug("PortForwardManager.Session connected local = " + local + " peer = " + peer);

			final PortForward send = new PortForward();
			send.command = PortForward.eConnect;
			send.portsid = sid;
			send.code = PortForward.eConnectV0;
			send.send(nio);
			canrecv = true;
			return true;
		}

		@Override
		public void shutdown(boolean eventually) throws CodecException {
			final PortForward send = new limax.switcher.switcherendpoint.PortForward();
			send.command = PortForward.eClose;
			send.portsid = sid;
			send.code = eventually ? PortForward.eCloseSessionClose : PortForward.eCloseSessionAbort;
			try {
				send.send(nio);
			} catch (InstantiationException | ClassCastException | SizePolicyException e) {
			}
		}

		@Override
		public void sendDataTo(byte[] data) throws CodecException {
			canrecv = false;
			if (Trace.isDebugEnabled())
				Trace.debug("PortForwardManager send eForward size = " + data.length + " canrecv = " + canrecv);

			final PortForward send = new PortForward();
			send.command = PortForward.eForward;
			send.portsid = sid;
			send.code = PortForward.eForwardRaw;
			send.data.swap(Octets.wrap(data));
			try {
				send.send(nio);
			} catch (InstantiationException | ClassCastException | SizePolicyException e) {
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
				final PortForward send = new limax.switcher.switcherendpoint.PortForward();
				send.command = limax.switcher.switcherendpoint.PortForward.eForwardAck;
				send.portsid = sid;
				send.code = 0;
				try {
					send.send(nio);
				} catch (InstantiationException | ClassCastException | SizePolicyException e) {
				}
				if (Trace.isDebugEnabled())
					Trace.debug("PortForwardManager send eForwardAck needsendack = " + needsendack);
			}
		}

		private void onForward(Octets data) {
			needsendack = true;
			if (Trace.isDebugEnabled())
				Trace.debug("PortForwardManager onForwardAck needsendack = " + needsendack);
			task.sendData(data.getByteBuffer());
		}

		private void onForwardAck() throws InstantiationException, SizePolicyException, CodecException {
			canrecv = true;
			if (Trace.isDebugEnabled())
				Trace.debug("PortForwardManager onForwardAck canrecv = " + canrecv);
			task.checkRecvMoreData();
		}
	}

	private Map<Transport, Map<Integer, Session>> smap = new HashMap<>();

	final void onPortForward(PortForward p) throws InstantiationException, SizePolicyException, CodecException {
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
				Trace.warn("PortForwardManager unknow command " + p);
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
		synchronized (smap) {
			final Map<Integer, Session> tm = smap.get(ss.nio);
			if (null == tm)
				return;
			tm.remove(ss.sid);
			if (tm.isEmpty())
				smap.remove(ss.nio);
		}
	}

	private Session getSession(Transport nio, int sid) {
		synchronized (smap) {
			final Map<Integer, Session> tm = smap.get(nio);
			if (null == tm)
				return null;
			else
				return tm.get(sid);
		}
	}

	private Session getSession(PortForward p) {
		return getSession(p.getTransport(), p.portsid);
	}

	private void onForwardAck(PortForward p) throws InstantiationException, SizePolicyException, CodecException {
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

		try {
			final OctetsStream os = new OctetsStream();
			os.swap(p.data);
			serverIp = os.unmarshal_String();
			serverPort = os.unmarshal_int();
		} catch (MarshalException e) {
			if (Trace.isWarnEnabled())
				Trace.warn("onConnect", e);
			sendClose(p, PortForward.eCloseUnknownConnectVersion);
			return;
		}

		final Session sesion = new Session(p.getTransport(), p.portsid);
		sesion.task = Transpond.createFlowControlClientTask(DefaultBufferSize, DefaultBufferSize, sesion);

		try {
			NetModel.addClient(new InetSocketAddress(serverIp, serverPort), sesion.task.getNetTask());
		} catch (IOException e) {
			if (Trace.isWarnEnabled())
				Trace.warn("onConnect serverIp = " + serverIp + " serverPort = " + serverPort, e);
			sendClose(p, PortForward.eCloseSessionAbort);
			return;
		}

		synchronized (smap) {
			Map<Integer, Session> tm = smap.get(p.getTransport());
			if (null == tm) {
				tm = new HashMap<Integer, Session>();
				smap.put(p.getTransport(), tm);
			}
			tm.put(p.portsid, sesion);
		}
	}
}
