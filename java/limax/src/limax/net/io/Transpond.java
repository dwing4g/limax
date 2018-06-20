package limax.net.io;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import limax.codec.CodecException;
import limax.util.Trace;

public class Transpond {

	private final static int DefaultBufferSize = 1024 * 16;

	public interface FlowControlProcessor {
		void shutdown(boolean eventually) throws CodecException;

		boolean setup(SocketAddress local, SocketAddress peer) throws Exception;

		void sendDataTo(byte[] data) throws CodecException;

		boolean isCanRecv();

		void sendDone() throws CodecException;
	}

	public interface FlowControlClientTask {

		void sendData(ByteBuffer buffer);

		void checkRecvMoreData() throws CodecException;

		void closeSession();

		NetTask getNetTask();
	}

	public interface FlowControlServerTask {

		void sendData(ByteBuffer buffer);

		void checkRecvMoreData() throws CodecException;

		void closeSession();

		void setTaskCounter(ServerContext context);

		void readyNow();

		NetTask getNetTask();
	}

	private static abstract class AbstractFlowControlTask extends NetTaskImpl {

		static private final class Processor implements NetProcessor {
			private AbstractFlowControlTask task;

			@Override
			public void process(byte[] in) {
			}

			@Override
			public void shutdown(boolean eventually, Throwable closeReason) {
				try {
					task.fcp.shutdown(eventually);
				} catch (Exception e) {
				}
			}

			@Override
			public void setup(NetTask nettask) {
				task = (AbstractFlowControlTask) nettask;
			}

			@Override
			public boolean setup(SocketAddress local, SocketAddress peer) throws Exception {
				return task.fcp.setup(local, peer);
			}
		};

		private volatile boolean sendbusy = false;
		protected final FlowControlProcessor fcp;
		private final int sendbuffersize;

		protected AbstractFlowControlTask(int rsize, int ssize, FlowControlProcessor fcp) {
			super(rsize, ssize, new Processor());
			sendbuffersize = ssize;
			this.fcp = fcp;
		}

		@Override
		synchronized void onExchange() {
			if (fcp.isCanRecv()) {
				final byte[] data = recv();
				if (data.length > 0)
					try {
						fcp.sendDataTo(data);
					} catch (Exception e) {
						try {
							fcp.shutdown(true);
						} catch (Exception e1) {
						}
					}
			}
		}

		public synchronized final void checkRecvMoreData() throws CodecException {
			final byte[] data = recv();
			if (data.length > 0)
				fcp.sendDataTo(data);
		}

		@Override
		public void send(ByteBuffer buffer) {
			super.send(buffer);
			sendbusy = getSendBufferSize() >= sendbuffersize;
		}

		public final boolean isSendBusy() {
			return sendbusy;
		}

		@Override
		public void onWriteBufferEmpty() {
			sendbusy = false;
			schedule(new Runnable() {
				@Override
				public void run() {
					try {
						fcp.sendDone();
					} catch (Exception e) {
						try {
							fcp.shutdown(true);
						} catch (Exception e1) {
						}
					}
				}
			});
		}

		public final void closeSession() {
			sendFinal();
		}

	}

	private static class FlowControlServerTaskImpl extends AbstractFlowControlTask {

		private AtomicInteger taskCounter;

		protected FlowControlServerTaskImpl(int rsize, int ssize, FlowControlProcessor fcp) {
			super(rsize, ssize, fcp);
		}

		public final void setTaskCounter(ServerContext context) {
			this.taskCounter = ((ServerContextImpl) context).taskCounter;
		}

		@Override
		protected void onBindKey() throws Exception {
			super.onBindKey();
			taskCounter.incrementAndGet();
		}

		@Override
		protected void onUnbindKey() {
			taskCounter.decrementAndGet();
		}

	}

	public static FlowControlServerTask createFlowControlServerTask(int rsize, int ssize, FlowControlProcessor fcp) {
		final FlowControlServerTaskImpl impl = new FlowControlServerTaskImpl(rsize, ssize, fcp);
		return new FlowControlServerTask() {

			@Override
			public void sendData(ByteBuffer buffer) {
				impl.send(buffer);
			}

			@Override
			public void checkRecvMoreData() throws CodecException {
				impl.checkRecvMoreData();
			}

			@Override
			public void closeSession() {
				impl.closeSession();
			}

			@Override
			public NetTask getNetTask() {
				return impl;
			}

			@Override
			public void setTaskCounter(ServerContext context) {
				impl.setTaskCounter(context);
			}

			@Override
			public void readyNow() {
				impl.enableReadWrite();
			}
		};
	}

	private static class FlowControlClientTaskImpl extends AbstractFlowControlTask {

		protected FlowControlClientTaskImpl(int rsize, int ssize, FlowControlProcessor fcp) {
			super(rsize, ssize, fcp);
		}

		@Override
		protected final void onBindKey() throws Exception {
			super.onBindKey();
		}

		@Override
		protected void onUnbindKey() {
		}
	}

	public static FlowControlClientTask createFlowControlClientTask(int rsize, int ssize, FlowControlProcessor fcp) {
		final FlowControlClientTaskImpl impl = new FlowControlClientTaskImpl(rsize, ssize, fcp);
		return new FlowControlClientTask() {

			@Override
			public void sendData(ByteBuffer buffer) {
				impl.send(buffer);
			}

			@Override
			public void checkRecvMoreData() throws CodecException {
				impl.checkRecvMoreData();
			}

			@Override
			public void closeSession() {
				impl.closeSession();
			}

			@Override
			public NetTask getNetTask() {
				return impl;
			}

		};
	}

	private static abstract class ProxySession implements FlowControlProcessor {

		protected AbstractFlowControlTask assocnettask = null;
		private ProxySession proxysession = null;

		@Override
		public void shutdown(boolean eventually) {
			proxysession.closeSession();
		}

		private void closeSession() {
			assocnettask.closeSession();
		}

		public void setAssocNetTask(AbstractFlowControlTask nettask) {
			assocnettask = nettask;
		}

		public void setProxySession(ProxySession ps) {
			proxysession = ps;
		}

		@Override
		public void sendDataTo(byte[] data) {
			proxysession.assocnettask.send(data);
		}

		@Override
		public boolean isCanRecv() {
			return null != proxysession && !assocnettask.isSendBusy();
		}

		@Override
		public void sendDone() throws CodecException {
			proxysession.assocnettask.checkRecvMoreData();
		}
	}

	private final static class ProxyClientTask extends FlowControlClientTaskImpl {

		static private final class ClientSession extends ProxySession {
			@Override
			public boolean setup(SocketAddress local, SocketAddress peer) throws Exception {
				return true;
			}
		}

		public ProxyClientTask(int rsize, int ssize, ProxyServerTask.ServerSession ps) {
			super(rsize, ssize, new ClientSession());
			final ProxySession ss = (ProxySession) fcp;
			ss.setAssocNetTask(this);
			ss.setProxySession(ps);
			ps.setProxySession(ss);
			ps.readyNow();
		}
	}

	private final static class ProxyServerTask extends FlowControlServerTaskImpl {

		private static class ServerSession extends ProxySession {
			@Override
			public boolean setup(SocketAddress local, SocketAddress peer) {
				final ProxyServerTask pst = (ProxyServerTask) assocnettask;
				if (pst.transpond.checkClient(peer))
					pst.doSetup();
				else
					assocnettask.closeSession();
				return false;
			}

			void readyNow() {
				assocnettask.enableReadWrite();
			}
		}

		private final Transpond transpond;

		public ProxyServerTask(Transpond transpond) {
			super(transpond.buffersize, transpond.buffersize, new ServerSession());
			this.transpond = transpond;
			final ServerSession ss = (ServerSession) fcp;
			ss.setAssocNetTask(this);
		}

		private void doSetup() {
			try {
				final ServerSession ss = (ServerSession) fcp;
				final ProxyClientTask dstnet = new ProxyClientTask(transpond.buffersize, transpond.buffersize, ss);
				NetModel.addClient(transpond.peerAddress, dstnet);
			} catch (IOException e) {
				if (Trace.isErrorEnabled())
					Trace.error(e);
			}
		}
	}

	private final SocketAddress peerAddress;
	private final int buffersize;

	private Transpond(SocketAddress peerAddress, int buffersize) {
		this.peerAddress = peerAddress;
		this.buffersize = buffersize;
	}

	private boolean checkClient(SocketAddress peer) {
		return true;
	}

	public static ServerContext startTranspond(SocketAddress localAddress, SocketAddress peerAddress)
			throws IOException {
		return startTranspond(localAddress, peerAddress, DefaultBufferSize);
	}

	public static ServerContext startTranspond(SocketAddress localAddress, SocketAddress peerAddress, int buffersize)
			throws IOException {
		final Transpond transpond = new Transpond(peerAddress, buffersize);
		return NetModel.addServer(localAddress, 0, buffersize, buffersize, new ServerContext.NetTaskConstructor() {

			@Override
			public NetTask newInstance(ServerContext context) {
				final ProxyServerTask task = new ProxyServerTask(transpond);
				task.setTaskCounter((ServerContextImpl) context);
				return task;
			}

			@Override
			public String getServiceName() {
				return transpond.getClass().getName();
			}
		}, true);
	}
}
