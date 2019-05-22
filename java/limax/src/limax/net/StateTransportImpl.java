package limax.net;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.concurrent.locks.ReentrantLock;

import limax.codec.BufferedSink;
import limax.codec.Codec;
import limax.codec.CodecException;
import limax.codec.Decrypt;
import limax.codec.Encrypt;
import limax.codec.NullCodec;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.codec.RFC2118Decode;
import limax.codec.RFC2118Encode;
import limax.net.io.NetProcessor;
import limax.net.io.NetTask;
import limax.util.Helper;
import limax.util.Trace;

final class StateTransportImpl extends AbstractTransport
		implements NetProcessor, StateTransport, SupportStateCheck, SupportTypedDataTransfer {
	private final AbstractManager manager;
	private final ReentrantLock lock = new ReentrantLock();
	private final ManagerConfig config;
	private volatile Codec input = NullCodec.getInstance();
	private volatile Codec output = NullCodec.getInstance();

	private volatile SocketAddress local;
	private volatile SocketAddress peer;
	private NetTask nettask;
	private volatile State state;

	StateTransportImpl(AbstractManager manager) {
		this.manager = manager;
		this.config = ((ManagerConfig) manager.getConfig());
		this.state = config.getDefaultState();
	}

	@Override
	public void process(byte[] data) throws Exception {
		if (Trace.isDebugEnabled())
			Trace.debug(manager + " " + this + " process size = " + data.length);
		try {
			input.update(data, 0, data.length);
			input.flush();
		} catch (Exception e) {
			if (Trace.isInfoEnabled())
				Trace.info(manager + " " + this + " process Exception : " + Helper.toHexString(data), e);
			throw e;
		}
	}

	@Override
	public void shutdown(Throwable closeReason) {
		boolean close;
		lock.lock();
		try {
			setCloseReason(closeReason);
			if (close = local != null)
				close();
		} finally {
			lock.unlock();
		}
		if (close)
			manager.removeProtocolTransport(this);
		else
			((ClientManagerImpl) manager).connectAbort(this);
	}

	@Override
	public boolean startup(NetTask nettask, SocketAddress local, SocketAddress peer) {
		lock.lock();
		try {
			this.nettask = nettask;
		} finally {
			lock.unlock();
		}
		this.local = local;
		this.peer = peer;
		setInputSecurityCodec(config.getInputSecurityBytes(), config.isInputCompress());
		setOutputSecurityCodec(config.getOutputSecurityBytes(), config.isOutputCompress());
		resetAlarm(0);
		manager.addProtocolTransport(this);
		return true;
	}

	@Override
	public void send(int type, Octets data) throws CodecException {
		sendData(new OctetsStream().marshal(type).marshal(data));
	}

	void sendData(Octets data) throws CodecException {
		if (Trace.isDebugEnabled())
			Trace.debug(manager + " " + this + " sendData size = " + data.size());
		lock.lock();
		try {
			output.update(data.array(), 0, data.size());
			output.flush();
		} finally {
			lock.unlock();
		}
	}

	@Override
	void close() {
		resetAlarm(0);
		lock.lock();
		try {
			if (nettask != null) {
				nettask.sendFinal();
				nettask = null;
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	public String toString() {
		return getClass().getName() + " (" + local + "-" + peer + ")[" + getSessionObject() + "]";
	}

	@Override
	public SocketAddress getPeerAddress() {
		return peer;
	}

	@Override
	public SocketAddress getLocalAddress() {
		return local;
	}

	private class NetTaskCodecSink implements Codec {
		@Override
		public void update(byte c) throws CodecException {
			update(new byte[] { c }, 0, 1);
		}

		@Override
		public void update(byte[] data, int off, int len) throws CodecException {
			if (nettask == null)
				throw new CodecException("nettask has been closed.");
			if (config.isCheckOutputBuffer()) {
				long sendbuffersize = nettask.getSendBufferSize() + len;
				long configsize = config.getOutputBufferSize();
				if (sendbuffersize > configsize) {
					if (Trace.isWarnEnabled())
						Trace.warn(manager + " " + this + " send buffer is full! sendbuffersize " + sendbuffersize + " "
								+ configsize);
					close();
					return;
				}
			}
			if (Trace.isDebugEnabled())
				Trace.debug(manager + " " + this + " send " + Helper.toHexString(data, off, len));
			nettask.send(data, off, len);
		}

		@Override
		public void flush() throws CodecException {
			if (nettask == null)
				throw new CodecException("nettask has been closed.");
		}
	}

	private class CodecSink implements Codec {
		private final OctetsStream os = new OctetsStream(8192);

		@Override
		public void update(byte c) throws CodecException {
			if (Trace.isDebugEnabled())
				Trace.debug(StateTransportImpl.this + " CodecSink size = 1");
			os.push_byte(c);
			try {
				dispatch(state.decode(os, StateTransportImpl.this));
			} catch (Exception e) {
				throw new CodecException(e);
			}
		}

		@Override
		public void update(byte[] data, int off, int len) throws CodecException {
			if (Trace.isDebugEnabled())
				Trace.debug(StateTransportImpl.this + " CodecSink size = " + len);
			os.insert(os.size(), data, off, len);
			try {
				dispatch(state.decode(os, StateTransportImpl.this));
			} catch (Exception e) {
				throw new CodecException(e);
			}
		}

		@Override
		public void flush() throws CodecException {
			try {
				if (os.remain() > 0)
					dispatch(state.decode(os, StateTransportImpl.this));
			} catch (Exception e) {
				throw new CodecException(e);
			}
		}

		private void dispatch(Collection<Skeleton> skels)
				throws InstantiationException, SizePolicyException, CodecException {
			for (final Skeleton skel : skels)
				skel.dispatch();
		}
	}

	@Override
	public void setOutputSecurityCodec(byte[] key, boolean compress) {
		if (Trace.isDebugEnabled())
			Trace.debug(manager + " " + this + " setOutputSecurityCodec key = "
					+ (key == null ? "" : Helper.toHexString(key)) + " compress = " + compress);
		Codec codec = new BufferedSink(new NetTaskCodecSink());
		if (null != key)
			try {
				codec = new Encrypt(codec, key);
			} catch (CodecException e) {
				if (Trace.isWarnEnabled())
					Trace.warn("setOutputEncrypt " + this, e);
			}
		if (compress)
			codec = new RFC2118Encode(codec);
		output = codec;
	}

	@Override
	public void setInputSecurityCodec(byte[] key, boolean compress) {
		if (Trace.isDebugEnabled())
			Trace.debug(manager + " " + this + " setInputSecurityCodec key = "
					+ (key == null ? "" : Helper.toHexString(key)) + " compress = " + compress);
		Codec codec = new CodecSink();
		if (compress || null != key)
			codec = new BufferedSink(codec);
		if (compress)
			codec = new RFC2118Decode(codec);
		if (null != key)
			try {
				codec = new Decrypt(codec, key);
			} catch (CodecException e) {
				if (Trace.isWarnEnabled())
					Trace.warn("setInputDecrypt " + this, e);
			}
		input = codec;
	}

	@Override
	public void setState(State state) {
		this.state = state;
	}

	@Override
	public Manager getManager() {
		return manager.getOutmostWrapperManager();
	}

	@Override
	public void check(int type, int size) throws InstantiationException, SizePolicyException {
		state.check(type, size);
	}

	@Override
	public void resetAlarm(long milliseconds) {
		lock.lock();
		try {
			if (nettask != null)
				nettask.resetAlarm(milliseconds);
		} finally {
			lock.unlock();
		}
	}
}
