package limax.http;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

import limax.codec.SHA1;
import limax.http.HttpServer.Parameter;
import limax.net.Engine;
import limax.net.io.NetTask;

class Http11Exchange extends AbstractHttpExchange implements ProtocolProcessor {
	private final static byte[] CRLF = new byte[] { 13, 10 };
	private final Http11Parser parser;
	private final long requestTimeout;
	private NetTask nettask;
	private boolean forceClose;

	Http11Exchange(HttpProcessor processor, NetTask nettask, long requestTimeout) {
		super(processor);
		parser = new Http11Parser(processor);
		this.requestTimeout = requestTimeout;
		this.nettask = nettask;
	}

	@Override
	protected void schedule(boolean done) {
		if (nettask != null)
			super.schedule(done);
	}

	@Override
	public Headers getRequestHeaders() {
		return parser.getHeaders();
	}

	@Override
	public Headers getRequestTrailers() {
		return parser.getTrailers();
	}

	@Override
	public long process(ByteBuffer in) {
		while (in.hasRemaining()) {
			if (parser.remain > 0) {
				int len = in.remaining();
				if (parser.remain > len) {
					parser.remain -= len;
					while (in.hasRemaining())
						formData.process(in.get());
					schedule(false);
					return requestTimeout;
				} else {
					for (int i = 0; i < parser.remain; i++)
						formData.process(in.get());
					schedule(false);
					parser.remainConsumed();
				}
			}
			while (in.hasRemaining() && parser.remain == 0)
				parser.process((char) in.get());
			if (parser.remain == 0)
				break;
			if (httpHandler == null) {
				if (parser.getVersion().equals(HttpVersion.HTTP2)) {
					processor.upgrade(new Http2Processor(processor, nettask, in));
					return (Long) processor.get(Parameter.HTTP2_SETTINGS_TIMEOUT);
				}
				Handler handler = getHandler(parser.getHeaders());
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
							processor.upgrade(new Http2Processor(processor, nettask, iheaders));
							sendResponseHeaders(oheaders, upgrade);
							return (Long) processor.get(Parameter.HTTP2_SETTINGS_TIMEOUT);
						}
					}
				} else {
					boolean websocket = upgrade.equals("websocket");
					websocket &= iheaders.getFirst("sec-websocket-version").equals("13");
					String key = iheaders.getFirst("sec-websocket-key");
					websocket &= key != null;
					if (websocket) {
						URI origin;
						try {
							origin = URI.create(iheaders.getFirst("origin"));
						} catch (Exception e) {
							origin = null;
						}
						processor.upgrade(new WebSocketExchangeImpl(nettask, handler, getLocalAddress(),
								new WebSocketAddress(getPeerAddress(), getRequestURI(), origin),
								(Integer) processor.get(Parameter.WEBSOCKET_MAX_INCOMING_MESSAGE_SIZE)));
						oheaders.set("sec-websocket-accept", Base64.getEncoder().encodeToString(
								SHA1.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes())));
						sendResponseHeaders(oheaders, "websocket");
					} else {
						oheaders.set(":status", HttpURLConnection.HTTP_BAD_REQUEST);
						oheaders.set("connection", "close");
						sendResponseHeaders(oheaders, 0L);
					}
					return 0;
				}
				formData = parser.remain == -1 ? new FormData(getRequestURI().getQuery())
						: createFormData(parser.getHeaders());
			}
			if (parser.remain == -1) {
				formData.end();
				schedule(true);
				return DISABLE_INCOMING;
			}
		}
		return requestTimeout;
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
	protected boolean sendResponseHeaders(Headers headers, Long length) {
		HttpVersion version = parser.getVersion();
		String connection = headers.getFirst("connection");
		if (connection != null && connection.equalsIgnoreCase("close")) {
			forceClose = true;
		} else if (parser.keepalive()) {
			if (version == HttpVersion.HTTP10)
				headers.set("connection", "keep-alive");
		} else {
			headers.set("connection", "close");
			forceClose = true;
		}
		boolean body = true;
		if (getRequestMethod().equals("head")) {
			body = false;
		} else if (length != null) {
			headers.set("content-length", length);
			if (length == 0L)
				body = false;
		} else if (version == HttpVersion.HTTP10) {
			headers.set("connection", "close");
			forceClose = true;
		} else {
			headers.set("transfer-encoding", "chunked");
		}
		nettask.send(StandardCharsets.ISO_8859_1.encode(
				headers.write(new StringBuilder(version.statusLine(Integer.parseInt(headers.getFirst(":status")))))
						.append("\r\n").toString()));
		if (!body)
			_sendFinal();
		return body;
	}

	@Override
	protected Runnable[] flowControl(Consumer<Integer> windowConsumer) {
		int initialWindow = (Integer) processor.get(Parameter.HTTP11_INITIAL_WINDOW_SIZE);
		long timeout = (Long) processor.get(Parameter.HTTP11_FLOWCONTROL_TIMEOUT);
		nettask.setSendBufferNotice((remain, att) -> {
			nettask.resetAlarm(timeout);
			int window = (int) (initialWindow - remain);
			if (window > 0)
				Engine.getApplicationExecutor().schedule(this, () -> windowConsumer.accept(window));
		}, null);
		return new Runnable[] { () -> windowConsumer.accept(initialWindow), () -> {
			nettask.resetAlarm(0);
			nettask.setSendBufferNotice(null, null);
		} };
	}

	@Override
	protected void send(ByteBuffer data, boolean chunk) {
		if (chunk)
			nettask.send(new ByteBuffer[] {
					StandardCharsets.ISO_8859_1.encode(Integer.toHexString(data.remaining()) + "\r\n"), data,
					ByteBuffer.wrap(CRLF) });
		else
			nettask.send(data);
	}

	@Override
	protected void send(ByteBuffer[] datas, boolean chunk) {
		if (chunk) {
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
	protected void sendFinal(ByteBuffer data) {
		nettask.send(data);
		_sendFinal();
	}

	@Override
	protected void sendFinal(ByteBuffer[] datas) {
		nettask.send(datas);
		_sendFinal();
	}

	@Override
	protected void sendFinal() {
		nettask.send(StandardCharsets.ISO_8859_1
				.encode(getResponseTrailers().write(new StringBuilder("0\r\n")).append("\r\n").toString()));
		_sendFinal();
	}

	private void _sendFinal() {
		if (forceClose) {
			nettask.sendFinal((Long) processor.get(Parameter.CLOSE_TIMEOUT));
			nettask = null;
		} else
			processor.pipeline();
	}

	@Override
	protected void forceClose() {
		forceClose = true;
	}
}