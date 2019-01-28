package limax.util.transpond;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import limax.net.io.NetProcessor;
import limax.net.io.NetTask;
import limax.util.Trace;

final class NetStation implements NetProcessor, FlowControlTask {
	private final FlowControlProcessor processor;
	private final int buffhalfsize;
	private NetTask nettask = null;
	private final AtomicBoolean exchange = new AtomicBoolean();

	NetStation(FlowControlProcessor processor, int buffersize) {
		this.processor = processor;
		this.buffhalfsize = buffersize / 2;
	}

	@Override
	public final void process(byte[] in) throws Exception {
		processor.sendDataTo(in);
	}

	@Override
	public final void shutdown(Throwable closeReason) {
		try {
			if (Trace.isDebugEnabled())
				Trace.debug("NetStation.shutdown closeReason " + processor.getClass().getName(), closeReason);
			processor.shutdown(null != nettask);
		} catch (Exception e) {
			if (Trace.isDebugEnabled())
				Trace.debug("NetStation.shutdown", e);
		}
	}

	@Override
	public final boolean startup(NetTask nettask, SocketAddress local, SocketAddress peer) throws Exception {
		if (Trace.isDebugEnabled())
			Trace.debug("NetStation.startup " + processor.getClass().getName());
		this.nettask = nettask;
		this.nettask.setSendBufferNotice(this::sendDone, processor);
		return processor.startup(this, local, peer);
	}

	final boolean isSendBusy() {
		return this.nettask.getSendBufferSize() >= buffhalfsize;
	}

	private void sendDone(long size, Object attachment) {
		try {
			processor.sendDone(size);
		} catch (Exception e) {
			if (Trace.isDebugEnabled())
				Trace.debug("NetStation.sendDone", e);
		}
	}

	@Override
	public void closeSession() {
		if (null != nettask)
			nettask.sendFinal();
	}

	@Override
	public void sendData(byte[] data) {
		this.nettask.send(data);
	}

	@Override
	public void sendData(ByteBuffer data) {
		this.nettask.send(data);
	}

	@Override
	public void disableReceive() {
		if (exchange.compareAndSet(true, false))
			this.nettask.disable();
	}

	@Override
	public void enableReceive() {
		if (exchange.compareAndSet(false, true))
			this.nettask.enable();
	}
}
