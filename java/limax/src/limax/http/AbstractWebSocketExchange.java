package limax.http;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import limax.http.HttpServer.Parameter;
import limax.http.RFC6455.RFC6455Exception;
import limax.http.WebSocketEvent.Type;
import limax.net.Engine;
import limax.util.Pair;

abstract class AbstractWebSocketExchange implements WebSocketExchange, ProtocolProcessor {
	final HttpProcessor processor;
	private final Consumer<WebSocketEvent> dispatcher;
	private final CustomSender sender;
	private final WebSocketAddress peer;
	private final RFC6455 server;
	private final long defaultFinalTimeout;
	private final AtomicLong serial = new AtomicLong();
	private int stage;
	private byte lastOpcode;
	private Object object;
	private Future<?> future;

	AbstractWebSocketExchange(WebSocketHandler handler, AbstractHttpExchange exchange) {
		URI origin;
		try {
			origin = URI.create(exchange.getRequestHeaders().getFirst("origin"));
		} catch (Exception e) {
			origin = null;
		}
		this.processor = exchange.processor;
		this.peer = new WebSocketAddress(exchange.getPeerAddress(), exchange.getContextURI(), exchange.getRequestURI(),
				origin);
		this.defaultFinalTimeout = (Long) processor.get(Parameter.WEBSOCKET_DEFAULT_FINAL_TIMEOUT);
		this.dispatcher = event -> {
			processor.execute(() -> {
				try {
					boolean close = event.type().equals(Type.CLOSE);
					switch (stage) {
					case 0:
						if (close) {
							stage = 2;
							return;
						}
						handler.handle(new WebSocketEvent(this, Type.OPEN, null));
						stage = 1;
						break;
					case 1:
						if (close)
							stage = 2;
						break;
					case 2:
						return;
					}
					handler.handle(event);
				} catch (RFC6455Exception e) {
					sendClose(e, defaultFinalTimeout);
				} catch (Exception e) {
					sendClose(new RFC6455Exception(RFC6455.closeServerException, e.getMessage()), defaultFinalTimeout);
				}
			});
		};
		this.sender = exchange
				.createWebSocketSender(() -> dispatcher.accept(new WebSocketEvent(this, Type.SENDREADY, null)));
		this.server = new RFC6455((Integer) processor.get(Parameter.WEBSOCKET_MAX_INCOMING_MESSAGE_SIZE), true);
	}

	private void close(short code, String reason) {
		resetAlarm(0);
		dispatcher.accept(new WebSocketEvent(this, Type.CLOSE, new Pair<>((short) code, reason)));
	}

	private void sendClose(RFC6455Exception e, long timeout) {
		server.sendClose(() -> {
			sender.send(server.wrap(RFC6455.opcodeClose, e.getCode()));
			sender.sendFinal(timeout);
		});
	}

	@Override
	public InetSocketAddress getLocalAddress() {
		return processor.getLocalAddress();
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

	@Override
	public long ping() {
		long serial = this.serial.incrementAndGet();
		ByteBuffer bb = ByteBuffer.allocate(16);
		bb.putLong(serial).putLong(System.currentTimeMillis());
		server.send(() -> sender.send(server.wrap(RFC6455.opcodePing, bb.array())));
		return serial;
	}

	@Override
	public void send(String text) {
		server.send(() -> sender.send(server.wrap(RFC6455.opcodeText, text.getBytes(StandardCharsets.UTF_8))));
	}

	@Override
	public void send(byte[] binary) {
		server.send(() -> sender.send(server.wrap(RFC6455.opcodeBinary, binary)));
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
	public void process(ByteBuffer in) {
		try {
			for (Queue<Pair<Byte, byte[]>> queue = server.unwrap(in); !queue.isEmpty();) {
				Pair<Byte, byte[]> pair = queue.poll();
				byte opcode = pair.getKey();
				byte data[] = pair.getValue();
				if (opcode == RFC6455.opcodeCont)
					opcode = lastOpcode;
				else
					lastOpcode = opcode;
				switch (opcode) {
				case RFC6455.opcodePing:
					server.send(() -> sender.send(server.wrap(RFC6455.opcodePong, data)));
					break;
				case RFC6455.opcodeText:
					dispatcher.accept(new WebSocketEvent(this, Type.TEXT, new String(data, StandardCharsets.UTF_8)));
					break;
				case RFC6455.opcodeBinary:
					dispatcher.accept(new WebSocketEvent(this, Type.BINARY, data));
					break;
				case RFC6455.opcodeClose:
					server.recvClose(data, (code, reason) -> close(code, reason));
					server.sendClose(() -> {
						sender.send(server.wrap(RFC6455.opcodeClose, data));
						sender.sendFinal(defaultFinalTimeout);
					});
					return;
				case RFC6455.opcodePong:
					Pair<Long, Long> pong;
					try {
						ByteBuffer bb = ByteBuffer.wrap(data);
						pong = new Pair<>(bb.getLong(), System.currentTimeMillis() - bb.getLong());
					} catch (Exception e) {
						pong = new Pair<>(-1L, -1L);
					}
					dispatcher.accept(new WebSocketEvent(this, Type.PONG, pong));
					break;
				default:
					throw new RFC6455Exception(RFC6455.closeNotSupportFrame, "Invalid Frame opcode = " + opcode);
				}
			}
		} catch (RFC6455Exception e) {
			sendClose(e, defaultFinalTimeout);
		}
	}

	@Override
	public void shutdown(Throwable closeReason) {
		server.recvClose(new RFC6455Exception(RFC6455.closeGoaway, closeReason.getMessage()).getCode(),
				(code, reason) -> close(code, reason));
	}

	abstract void executeAlarmTask();

	@Override
	public void resetAlarm(long milliseconds) {
		synchronized (serial) {
			if (future != null)
				future.cancel(false);
			future = milliseconds > 0 ? Engine.getProtocolScheduler().scheduleWithFixedDelay(() -> {
				synchronized (serial) {
					future.cancel(false);
					future = null;
				}
				executeAlarmTask();
			}, milliseconds, milliseconds, TimeUnit.MILLISECONDS) : null;
		}
	}
}
