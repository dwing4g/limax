package limax.net.io;

import java.nio.ByteBuffer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;

abstract class SSLTask extends NetTaskImpl implements SSLSwitcher {
	private final SSLContext sslContext;
	private boolean sslON;
	private SSLEngine engine;
	private ByteBuffer netDataIn;
	private ByteBuffer netDataOut;
	private ByteBuffer appDataIn;
	private ByteBuffer appDataOut;
	private boolean delayedSendFinal = false;
	private boolean shuttingdown = false;

	protected SSLTask(int rsize, int wsize, NetProcessor processor, SSLContext sslContext) {
		super(rsize, wsize, processor);
		this.sslContext = sslContext;
		this.sslON = false;
	}

	private SSLEngineResult wrap() throws SSLException {
		while (true) {
			SSLEngineResult rs = engine.wrap(appDataOut, netDataOut);
			if (netDataOut.position() > 0) {
				netDataOut.flip();
				super.send(netDataOut);
				netDataOut = netDataOut.duplicate().compact().slice();
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
			SSLEngineResult rs = engine.unwrap(netDataIn, appDataIn);
			switch (rs.getStatus()) {
			case BUFFER_OVERFLOW:
				appDataIn.flip();
				appDataIn = ByteBuffer
						.allocateDirect(appDataIn.remaining() + engine.getSession().getApplicationBufferSize())
						.put(appDataIn);
				break;
			case BUFFER_UNDERFLOW:
				if (netDataIn.capacity() < engine.getSession().getPacketBufferSize())
					netDataIn = ByteBuffer.allocateDirect(engine.getSession().getPacketBufferSize()).put(netDataIn);
			default:
				netDataIn.compact();
				return rs;
			}
		}
	}

	private boolean processResult(SSLEngineResult rs) throws SSLException {
		switch (rs.getHandshakeStatus()) {
		case NEED_TASK:
			for (Runnable task; (task = engine.getDelegatedTask()) != null; task.run())
				;
			break;
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
			}
		case NOT_HANDSHAKING:
			if (rs.getStatus() != Status.CLOSED)
				while (appDataOut.hasRemaining())
					wrap();
		default:
		}
		if (rs.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING && rs.getStatus() == Status.CLOSED) {
			sslON = false;
			appDataIn.put(netDataIn);
			if (appDataOut.hasRemaining()) {
				super.send(appDataOut.duplicate());
				appDataOut.clear();
			}
			if (delayedSendFinal)
				super.sendFinal();
		}
		return rs.getStatus() == Status.OK;
	}

	@Override
	synchronized byte[] recv() {
		if (!sslON)
			return super.recv();
		ByteBuffer rbuf = getReaderBuffer();
		synchronized (rbuf) {
			rbuf.flip();
			int len = netDataIn.position() + rbuf.remaining();
			if (len > netDataIn.capacity()) {
				netDataIn.flip();
				netDataIn = ByteBuffer.allocateDirect(len).put(netDataIn);
			}
			netDataIn.put(rbuf);
			rbuf.clear();
		}
		try {
			while (processResult(unwrap()))
				;
		} catch (SSLException e) {
			super.close(e);
		}
		appDataIn.flip();
		byte[] data = new byte[appDataIn.remaining()];
		appDataIn.get(data).compact();
		return data;
	}

	@Override
	public synchronized void send(ByteBuffer buffer) {
		if (!sslON) {
			super.send(buffer);
			return;
		}
		appDataOut = buffer;
		try {
			processResult(wrap());
		} catch (SSLException e) {
			super.close(e);
		}
	}

	@Override
	public synchronized void sendFinal() {
		if (sslON) {
			detach();
			delayedSendFinal = true;
		} else
			super.sendFinal();
	}

	@Override
	public synchronized void attach(String host, int port, boolean clientMode, byte[] sendBeforeHandshake) {
		if (sslON)
			return;
		engine = sslContext.createSSLEngine(host, port);
		engine.setUseClientMode(clientMode);
		netDataIn = ByteBuffer.allocateDirect(engine.getSession().getPacketBufferSize());
		netDataOut = ByteBuffer.allocateDirect(engine.getSession().getPacketBufferSize() * 2);
		appDataIn = ByteBuffer.allocateDirect(engine.getSession().getApplicationBufferSize());
		appDataOut = ByteBuffer.allocateDirect(0);
		sslON = true;
		if (sendBeforeHandshake != null)
			super.send(ByteBuffer.wrap(sendBeforeHandshake));
	}

	@Override
	public synchronized void detach() {
		if (!sslON)
			return;
		try {
			shuttingdown = true;
			if (engine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING)
				processResult(new SSLEngineResult(Status.CLOSED, HandshakeStatus.FINISHED, 0, 0));
		} catch (SSLException e) {
			super.close(e);
		}
	}
}
