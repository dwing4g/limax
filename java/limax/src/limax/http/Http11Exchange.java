package limax.http;

import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;
import java.util.function.Function;

import limax.codec.SHA1;
import limax.http.HttpServer.Parameter;
import limax.net.io.NetTask;

class Http11Exchange extends AbstractHttpExchange implements ProtocolProcessor {
	private final static byte[] CRLF = new byte[] { 13, 10 };
	private final Http11Parser parser;
	private final NetTask nettask;
	private final FlowControlFlusher flusher;
	private final long requestTimeout;
	private ByteBuffer remain;

	Http11Exchange(HttpProcessor processor, long requestTimeout) {
		super(processor);
		this.parser = new Http11Parser(processor);
		this.nettask = processor.nettask();
		this.flusher = new FlowControlFlusher(processor);
		this.requestTimeout = requestTimeout;
	}

	@Override
	public Headers getRequestHeaders() {
		return parser.getHeaders();
	}

	@Override
	public Headers getRequestTrailers() {
		return parser.getTrailers();
	}

	private long _process(ByteBuffer in) throws Exception {
		while (in.hasRemaining()) {
			if (parser.remain > 0) {
				int len = in.remaining();
				if (parser.remain > len) {
					parser.remain -= len;
					while (in.hasRemaining())
						formData.process(in.get());
					httpHandler.censor(this);
					return requestTimeout;
				} else {
					for (int i = 0; i < parser.remain; i++)
						formData.process(in.get());
					httpHandler.censor(this);
					parser.remainConsumed();
				}
			}
			while (in.hasRemaining() && parser.remain == 0)
				parser.process((char) in.get());
			if (parser.remain == 0)
				break;
			if (httpHandler == null) {
				if (parser.getVersion().equals(HttpVersion.HTTP2)) {
					processor.replace(new Http2Processor(processor, nettask, in));
					return (Long) processor.get(Parameter.HTTP2_SETTINGS_TIMEOUT);
				}
				Handler handler = find(parser.getHeaders());
				Headers iheaders = parser.getHeaders();
				Headers oheaders = getResponseHeaders();
				String upgrade = iheaders.getFirst("upgrade");
				if (handler instanceof HttpHandler) {
					httpHandler = (HttpHandler) handler;
					if (upgrade != null) {
						boolean http2 = false;
						switch (upgrade) {
						case "h2":
							http2 = nettask.getSSLSession() != null;
							break;
						case "h2c":
							http2 = true;
							break;
						}
						if (http2) {
							sendResponseHeaders(oheaders, upgrade);
							processor.replace(new Http2Processor(processor, nettask, iheaders));
							return (Long) processor.get(Parameter.HTTP2_SETTINGS_TIMEOUT);
						}
					}
				} else {
					boolean check = upgrade.equals("websocket");
					check &= iheaders.getFirst("sec-websocket-version").equals("13");
					String key = iheaders.getFirst("sec-websocket-key");
					check &= key != null;
					if (check) {
						processor.replace(new WebSocket11Exchange((WebSocketHandler) handler, this));
						oheaders.set("sec-websocket-accept", Base64.getEncoder().encodeToString(
								SHA1.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes())));
						sendResponseHeaders(oheaders, upgrade);
					} else {
						oheaders.set(":status", HttpURLConnection.HTTP_BAD_REQUEST);
						oheaders.set("connection", "close");
						sendResponseHeaders(oheaders, 0L);
					}
					return 0;
				}
				formData = parser.remain == -1 ? new FormData(getRequestURI()) : new FormData(parser.getHeaders());
			}
			if (parser.remain == -1) {
				remain = in;
				formData.end(false);
				async(httpHandler);
				return -1;
			}
		}
		return requestTimeout;
	}

	@Override
	public void process(ByteBuffer in) throws Exception {
		long timeout = _process(in);
		if (timeout >= 0) {
			nettask.resetAlarm(timeout);
		} else {
			nettask.resetAlarm(0);
			nettask.disable();
		}
	}

	private void pipeline() {
		try {
			Http11Exchange exchange = new Http11Exchange(processor, requestTimeout);
			processor.replace(exchange);
			long timeout = exchange._process(remain);
			if (timeout >= 0) {
				nettask.resetAlarm(timeout);
				nettask.enable();
			} else {
				nettask.resetAlarm(0);
			}
		} catch (Exception e) {
			cancel(e);
		}
	}

	@Override
	public void cancel(Throwable closeReason) {
		nettask.cancel(closeReason);
	}

	@Override
	public void shutdown(Throwable closeReason) {
		cancel();
	}

	private void sendResponseHeaders(Headers headers, String upgrade) {
		headers.set("connection", "upgrade");
		headers.set("upgrade", upgrade);
		nettask.send(StandardCharsets.ISO_8859_1.encode(
				headers.write(new StringBuilder(parser.getVersion().statusLine(101))).append("\r\n").toString()));
	}

	@Override
	boolean sendResponseHeaders(Headers headers, Long length) {
		HttpVersion version = parser.getVersion();
		String connection = headers.getFirst("connection");
		if (connection != null && connection.equalsIgnoreCase("close")) {
			forceClose = true;
		} else if (parser.keepalive()) {
			if (version == HttpVersion.HTTP10)
				headers.set("connection", "keep-alive");
		} else {
			forceClose(headers);
		}
		boolean body = true;
		if (getRequestMethod().equals("HEAD")) {
			body = false;
		} else if (length != null) {
			headers.set("content-length", length);
			if (length == 0L)
				body = false;
		} else if (version == HttpVersion.HTTP10) {
			forceClose(headers);
		} else {
			headers.set("transfer-encoding", "chunked");
		}
		flusher.alarm(true);
		nettask.send(StandardCharsets.ISO_8859_1.encode(
				headers.write(new StringBuilder(version.statusLine(Integer.parseInt(headers.getFirst(":status")))))
						.append("\r\n").toString()));
		if (!body) {
			flusher.setSendBufferNotice(() -> {
				flusher.alarm(true);
				flusher.flush();
			});
			_sendFinal();
		}
		return body;
	}

	@Override
	void flowControl(Function<Integer, Boolean> windowConsumer) {
		flusher.flowControl(windowConsumer);
	}

	@Override
	void send(ByteBuffer data, boolean chunk) {
		if (chunk && !forceClose)
			nettask.send(new ByteBuffer[] {
					StandardCharsets.ISO_8859_1.encode(Integer.toHexString(data.remaining()) + "\r\n"), data,
					ByteBuffer.wrap(CRLF) });
		else
			nettask.send(data);
	}

	@Override
	void send(ByteBuffer[] datas, boolean chunk) {
		if (chunk && !forceClose) {
			int remaining = 0;
			for (ByteBuffer bb : datas)
				remaining += bb.remaining();
			ByteBuffer[] bbs = new ByteBuffer[datas.length + 2];
			bbs[0] = StandardCharsets.ISO_8859_1.encode(Integer.toHexString(remaining) + "\r\n");
			System.arraycopy(datas, 0, bbs, 1, datas.length);
			bbs[bbs.length - 1] = ByteBuffer.wrap(CRLF);
			nettask.send(bbs);
		} else
			nettask.send(datas);
	}

	@Override
	void sendFinal(ByteBuffer[] datas) {
		nettask.send(datas);
		_sendFinal();
	}

	@Override
	void sendFinal() {
		if (!forceClose)
			nettask.send(StandardCharsets.ISO_8859_1
					.encode(getResponseTrailers().write(new StringBuilder("0\r\n")).append("\r\n").toString()));
		_sendFinal();
	}

	private void _sendFinal() {
		flusher.flush(() -> {
			if (forceClose)
				nettask.sendFinal((Long) processor.get(Parameter.HTTP11_CLOSE_TIMEOUT));
			else
				nettask.execute(() -> pipeline());
		});
	}

	@Override
	void forceClose(Headers headers) {
		forceClose = true;
		headers.set("connection", "close");
	}

	private static class FlowControlFlusher {
		private final HttpProcessor processor;
		private final NetTask nettask;
		private final long timeout;
		private boolean active = true;
		private Runnable flushing;

		FlowControlFlusher(HttpProcessor processor) {
			this.processor = processor;
			this.nettask = processor.nettask();
			this.timeout = (Long) processor.get(Parameter.CONGESTION_TIMEOUT);
		}

		boolean flush() {
			if (!active && flushing != null)
				flushing.run();
			return active;
		}

		void flush(Runnable lastAction) {
			active = false;
			flushing = () -> {
				if (getSendBufferSize() == 0) {
					processor.setSendBufferNotice(null);
					lastAction.run();
					flushing = null;
				}
			};
			flushing.run();
		}

		void alarm(boolean on) {
			nettask.resetAlarm(on ? timeout : 0);
		}

		long getSendBufferSize() {
			return nettask.getSendBufferSize();
		}

		Runnable setSendBufferNotice(Runnable action) {
			processor.setSendBufferNotice(action);
			return action;
		}

		void flowControl(Function<Integer, Boolean> windowConsumer) {
			int flowControlWindow = (Integer) processor.get(Parameter.FLOWCONTROL_WINDOW_SIZE);
			setSendBufferNotice(() -> {
				alarm(true);
				if (flush()) {
					int window = (int) (flowControlWindow - getSendBufferSize());
					if (window > 0)
						windowConsumer.apply(window);
				}
			}).run();
		}
	}

	private static class Http11Sender implements CustomSender {
		private final FlowControlFlusher flusher;
		private final Consumer<ByteBuffer> c0;
		private final Consumer<Long> c1;

		Http11Sender(FlowControlFlusher flusher, Consumer<ByteBuffer> c0, Consumer<Long> c1, Runnable onSendReady) {
			(this.flusher = flusher).setSendBufferNotice(() -> {
				synchronized (flusher) {
					if (flusher.flush() && flusher.getSendBufferSize() == 0) {
						flusher.alarm(false);
						onSendReady.run();
					} else {
						flusher.alarm(true);
					}
				}
			});
			this.c0 = c0;
			this.c1 = c1;
		}

		@Override
		public void send(ByteBuffer bb) {
			synchronized (flusher) {
				if (flusher.flush())
					c0.accept(bb);
			}
		}

		@Override
		public void sendFinal(long timeout) {
			synchronized (flusher) {
				if (flusher.flush())
					c1.accept(timeout);
			}
		}
	}

	@Override
	CustomSender createWebSocketSender(Runnable onSendReady) {
		return new Http11Sender(flusher, bb -> flusher.nettask.send(bb),
				timeout -> flusher.flush(() -> flusher.nettask.sendFinal(timeout)), onSendReady);
	}

	@Override
	CustomSender createCustomSender(Runnable onSendReady, Runnable onClose) {
		return new Http11Sender(flusher, bb -> send(bb, true), timeout -> {
			sendFinal();
			onClose.run();
		}, onSendReady);
	}
}
