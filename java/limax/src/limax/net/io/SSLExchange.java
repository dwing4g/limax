package limax.net.io;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

class SSLExchange {
	private final SSLContext sslContext;
	private final int sslMode;
	private Exchange exchange;
	private boolean sslON;

	public boolean isSSLSupported() {
		return sslContext != null;
	}

	SSLExchange(SSLContext sslContext, int sslMode) {
		this.sslContext = sslContext;
		this.sslMode = sslMode;
	}

	void attach(AbstractNetTask task, String host, int port, SSLEngineDecorator decorator, byte[] negotiationData) {
		if (sslContext != null && !sslON)
			exchange = new Exchange(task, host, port, decorator, negotiationData);
	}

	void detach() {
		exchange.detach();
	}

	void renegotiate() {
		exchange.renegotiate();
	}

	boolean on() {
		return sslON;
	}

	void send(ByteBuffer[] bbs) {
		exchange.send(bbs);
	}

	void send(ByteBuffer bb) {
		exchange.send(bb);
	}

	void sendFinal(long timeout) {
		exchange.sendFinal(timeout);
	}

	ByteBuffer recv(ByteBuffer bb) {
		return exchange.recv(bb);
	}

	SSLSession getSSLSession() {
		return exchange.getSSLSession();
	}

	private static ByteBuffer append(ByteBuffer dst, ByteBuffer src) {
		int len = dst.position() + src.remaining();
		if (len > dst.capacity()) {
			dst.flip();
			dst = ByteBuffer.allocate(len).put(dst);
		}
		return dst.put(src);
	}

	private class Exchange {
		private final AbstractNetTask task;
		private SSLEngine engine;
		private ByteBuffer netDataIn;
		private ByteBuffer netDataOut;
		private ByteBuffer appDataIn;
		private final Deque<ByteBuffer> appDataOut = new ArrayDeque<ByteBuffer>();
		private Long delayedSendFinal;
		private boolean renegotiate = false;
		private boolean shuttingdown = false;

		public Exchange(AbstractNetTask task, String host, int port, SSLEngineDecorator decorator,
				byte[] negotiationData) {
			this.task = task;
			SSLEngine e = sslContext.createSSLEngine(host, port);
			engine = decorator == null ? e : decorator.decorate(e);
			netDataIn = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
			netDataOut = ByteBuffer.allocateDirect(engine.getSession().getPacketBufferSize() * 2);
			appDataIn = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
			if ((sslMode & NetModel.SSL_SERVER_MODE) != 0) {
				engine.setUseClientMode(false);
				engine.setNeedClientAuth((sslMode & NetModel.SSL_NEED_CLIENT_AUTH) != 0);
				engine.setWantClientAuth((sslMode & NetModel.SSL_WANT_CLIENT_AUTH) != 0);
			} else {
				engine.setUseClientMode(true);
				send(ByteBuffer.allocate(0));
			}
			if (negotiationData != null)
				recv(ByteBuffer.wrap(negotiationData));
			sslON = true;
		}

		private SSLEngineResult wrap() throws SSLException {
			while (true) {
				SSLEngineResult rs = engine.wrap(appDataOut.toArray(new ByteBuffer[0]), netDataOut);
				if (netDataOut.position() > 0) {
					while (!appDataOut.isEmpty() && !appDataOut.peek().hasRemaining())
						appDataOut.poll();
					ByteBuffer tmp = netDataOut;
					netDataOut = tmp.slice();
					tmp.flip();
					task.output(tmp);
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
							.allocate(appDataIn.remaining() + engine.getSession().getApplicationBufferSize())
							.put(appDataIn);
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
					while (!appDataOut.isEmpty())
						wrap();
			default:
			}
			if (rs.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING) {
				if (rs.getStatus() == Status.CLOSED) {
					if (netDataIn.position() > 0) {
						netDataIn.flip();
						appDataIn = append(appDataIn, netDataIn);
					}
					while (!appDataOut.isEmpty())
						task.output(appDataOut.poll());
					if (delayedSendFinal != null)
						task.outputFinal(delayedSendFinal);
					sslON = false;
				} else if (renegotiate) {
					engine.getSession().invalidate();
					engine.beginHandshake();
					renegotiate = false;
				}
			}
			return rs.getStatus() == Status.OK;
		}

		ByteBuffer recv(ByteBuffer rbuf) {
			netDataIn = append(netDataIn, rbuf);
			rbuf.clear();
			try {
				while (processResult(unwrap()))
					;
			} catch (SSLException e) {
				task.close(e);
			}
			appDataIn.flip();
			return appDataIn;
		}

		void send(ByteBuffer[] bbs) {
			for (ByteBuffer bb : bbs)
				appDataOut.offer(bb);
			flush();
		}

		void send(ByteBuffer bb) {
			appDataOut.offer(bb);
			flush();
		}

		private void flush() {
			try {
				processResult(wrap());
			} catch (SSLException e) {
				task.close(e);
			}
		}

		void sendFinal(long timeout) {
			detach();
			delayedSendFinal = timeout;
		}

		void detach() {
			try {
				shuttingdown = true;
				if (engine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING)
					processResult(new SSLEngineResult(Status.CLOSED, HandshakeStatus.FINISHED, 0, 0));
			} catch (SSLException e) {
				task.close(e);
			}
		}

		void renegotiate() {
			renegotiate = true;
		}

		SSLSession getSSLSession() {
			return engine.getSession();
		}
	}
}
