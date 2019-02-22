package limax.http;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLSession;

import limax.http.RFC6455.RFC6455Exception;
import limax.net.Engine;
import limax.net.io.NetTask;
import limax.util.Pair;

class WebSocketExchangeImpl implements WebSocketExchange, ProtocolProcessor {
	private final NetTask nettask;
	private final WebSocketHandler handler;
	private final InetSocketAddress local;
	private final WebSocketAddress peer;
	private final RFC6455 server;
	private final AtomicLong serial = new AtomicLong();
	private byte lastOpcode;
	private Object object;

	WebSocketExchangeImpl(NetTask nettask, Handler handler, InetSocketAddress local, WebSocketAddress peer,
			int maxMessageSize) {
		this.nettask = nettask;
		this.handler = (WebSocketHandler) handler;
		this.local = local;
		this.peer = peer;
		this.server = new RFC6455(maxMessageSize, true);
	}

	private void handle(WebSocketEvent event) {
		Engine.getApplicationExecutor().execute(this, () -> {
			try {
				handler.handle(event);
			} catch (RFC6455Exception e) {
				sendClose(e, 0);
			} catch (Exception e) {
				sendClose(new RFC6455Exception(RFC6455.closeServerException, e.getMessage()), 0);
			}
		});
	}

	private void close(short code, String reason) {
		handle(new WebSocketEvent(this, null, null, null, new Pair<>((short) code, reason)));
	}

	@Override
	public long process(ByteBuffer in) {
		try {
			for (Queue<Pair<Byte, byte[]>> queue = server.unwrap(in.array()); !queue.isEmpty();) {
				Pair<Byte, byte[]> pair = queue.poll();
				byte opcode = pair.getKey();
				byte data[] = pair.getValue();
				if (opcode == RFC6455.opcodeCont)
					opcode = lastOpcode;
				else
					lastOpcode = opcode;
				switch (opcode) {
				case RFC6455.opcodePing:
					server.send(() -> nettask.send(server.wrap(RFC6455.opcodePong, data)));
					break;
				case RFC6455.opcodeText:
					handle(new WebSocketEvent(this, new String(data, StandardCharsets.UTF_8), null, null, null));
					break;
				case RFC6455.opcodeBinary:
					handle(new WebSocketEvent(this, null, data, null, null));
					break;
				case RFC6455.opcodeClose:
					server.recvClose(data, (code, reason) -> close(code, reason));
					server.sendClose(() -> {
						nettask.send(server.wrap(RFC6455.opcodeClose, data));
						nettask.sendFinal();
					});
					return NO_RESET;
				case RFC6455.opcodePong:
					Pair<Long, Long> pong;
					try {
						ByteBuffer bb = ByteBuffer.wrap(data);
						pong = new Pair<>(bb.getLong(), System.currentTimeMillis() - bb.getLong());
					} catch (Exception e) {
						pong = new Pair<>(-1L, -1L);
					}
					handle(new WebSocketEvent(this, null, null, pong, null));
					break;
				default:
					throw new RFC6455Exception(RFC6455.closeNotSupportFrame, "Invalid Frame opcode = " + opcode);
				}
			}
		} catch (RFC6455Exception e) {
			sendClose(e, 0);
		}
		return NO_RESET;
	}

	@Override
	public void shutdown(Throwable closeReason) {
		server.recvClose(new RFC6455Exception(RFC6455.closeNetException, closeReason.getMessage()).getCode(),
				(code, reason) -> close(code, reason));
	}

	@Override
	public InetSocketAddress getLocalAddress() {
		return local;
	}

	@Override
	public WebSocketAddress getPeerAddress() {
		return peer;
	}

	@Override
	public void setSessionObject(Object object) {
		this.object = object;
	}

	@Override
	public Object getSessionObject() {
		return object;
	}

	private void sendClose(RFC6455Exception e, long timeout) {
		server.sendClose(() -> {
			nettask.send(server.wrap(RFC6455.opcodeClose, e.getCode()));
			nettask.sendFinal(timeout);
		});
	}

	@Override
	public long ping() {
		long serial = this.serial.incrementAndGet();
		ByteBuffer bb = ByteBuffer.allocate(16);
		bb.putLong(serial).putLong(System.currentTimeMillis());
		server.send(() -> nettask.send(server.wrap(RFC6455.opcodePing, bb.array())));
		return serial;
	}

	@Override
	public void send(String text) {
		server.send(() -> nettask.send(server.wrap(RFC6455.opcodeText, text.getBytes(StandardCharsets.UTF_8))));
	}

	@Override
	public void send(byte[] binary) {
		server.send(() -> nettask.send(server.wrap(RFC6455.opcodeBinary, binary)));
	}

	@Override
	public void sendFinal(long timeout) {
		sendClose(new RFC6455Exception(RFC6455.closeNormal, "Close Normal"), timeout);
	}

	@Override
	public void sendFinal() {
		sendFinal(0);
	}

	@Override
	public void enable() {
		nettask.enable();
	}

	@Override
	public void disable() {
		nettask.disable();
	}

	@Override
	public void resetAlarm(long millisecond) {
		nettask.resetAlarm(millisecond);
	}

	@Override
	public SSLSession getSSLSession() {
		return nettask.getSSLSession();
	}
}
