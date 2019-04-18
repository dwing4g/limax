package limax.http;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.net.ssl.SSLSession;

import limax.http.HttpServer.Parameter;
import limax.http.RFC7540.Connection;
import limax.http.RFC7540.ErrorCode;
import limax.http.RFC7540.Processor;
import limax.http.RFC7540.Stream;
import limax.http.RFC7541.Entry;

class Http2Exchange extends AbstractHttpExchange implements Processor {
	private final static ByteBuffer EMPTY = ByteBuffer.allocate(0);
	private final static Headers EMPTY_TRAILERS = new Headers();
	private final Connection connection;
	private final Stream stream;
	private Headers headers;
	private Headers trailers;
	private boolean forceClose;

	Http2Exchange(HttpProcessor processor, Connection connection, Stream stream, Headers headers) {
		super(new ApplicationExecutor(stream), processor);
		this.connection = connection;
		this.stream = stream;
		if (headers != null) {
			process(headers);
			end();
		}
	}

	private void process(Headers headers) {
		if (this.headers == null) {
			Handler handler = find(this.headers = headers);
			if (handler instanceof HttpHandler)
				httpHandler = (HttpHandler) handler;
			else {
				URI origin;
				try {
					origin = URI.create(headers.getFirst("origin"));
				} catch (Exception e) {
					origin = null;
				}
				stream.startup(
						new RFC8441(executor, connection, stream, (WebSocketHandler) handler,
								onSendReady -> createWebSocketSender(onSendReady), processor.getLocalAddress(),
								new WebSocketAddress(processor.getPeerAddress(), getContextURI(), getRequestURI(),
										origin),
								(Integer) processor.get(Parameter.WEBSOCKET_MAX_INCOMING_MESSAGE_SIZE),
								(Long) processor.get(Parameter.WEBSOCKET_DEFAULT_FINAL_TIMEOUT)));
				stream.sendHeaders(Collections.singletonList(new Entry(":status", "200")), false);
			}
		}
		if (this.trailers == null)
			this.trailers = headers;
	}

	@Override
	public void process(List<Entry> headers) {
		Headers tmp = new Headers();
		headers.forEach(e -> tmp.add(e.getKey(), e.getValue()));
		process(tmp);
	}

	@Override
	public void process(ByteBuffer in) {
		if (formData == null)
			formData = new FormData(headers, httpHandler.postLimit());
		int remaining = in.remaining();
		for (int i = 0; i < remaining; i++)
			formData.process(in.get());
		stream.windowUpdate(remaining);
		schedule(false);
	}

	@Override
	public void end() {
		if (formData == null)
			formData = new FormData(getRequestURI());
		formData.end(false);
		schedule(true);
	}

	@Override
	public void shutdown(Throwable closeReason) {
		cancel();
	}

	@Override
	public void reset(ErrorCode errorCode) {
		cancel();
	}

	@Override
	public Headers getRequestHeaders() {
		return headers;
	}

	@Override
	public Headers getRequestTrailers() {
		return trailers;
	}

	@Override
	public SSLSession getSSLSession() {
		return connection.getSSLSession();
	}

	@Override
	public void promise(URI uri) {
		Headers headers = this.headers.copy();
		headers.set(":method", "GET");
		headers.set(":path", uri.getPath());
		Stream promise = stream.sendPromise(transform(headers));
		if (promise != null)
			promise.startup(new Http2Exchange(processor, connection, promise, headers));
	}

	private static List<Entry> transform(Headers headers) {
		List<Entry> r = new ArrayList<>();
		for (Map.Entry<String, List<String>> e : headers.entrySet())
			for (String value : e.getValue())
				if (e.getKey().charAt(0) == ':')
					r.add(new Entry(e.getKey(), value));
		for (Map.Entry<String, List<String>> e : headers.entrySet())
			for (String value : e.getValue())
				if (e.getKey().charAt(0) != ':')
					r.add(new Entry(e.getKey(), value));
		return r;
	}

	@Override
	protected boolean sendResponseHeaders(Headers headers, Long length) {
		boolean body = true;
		if (getRequestMethod().equals("head")) {
			body = false;
		} else if (length != null) {
			headers.set("content-length", length);
			if (length == 0L)
				body = false;
		}
		headers.remove("connection");
		stream.sendHeaders(transform(headers), !body);
		return body;
	}

	@Override
	protected void flowControl(Function<Integer, Boolean> windowConsumer) {
		Consumer<Integer> partitionConsumer = partitioner().apply(windowConsumer);
		Runnable pump = () -> executor.executeExclusively(() -> partitionConsumer.accept(stream.getWindowSize()));
		stream.setPump(pump);
		pump.run();
	}

	@Override
	protected void send(ByteBuffer data, boolean chunk) {
		stream.sendData(data, false);
	}

	@Override
	protected void send(ByteBuffer[] datas, boolean chunk) {
		stream.sendData(datas, false);
	}

	@Override
	protected void sendFinal(ByteBuffer[] datas) {
		sendFinal(stream, datas, getResponseTrailers());
		if (forceClose)
			connection.goway();
	}

	@Override
	protected void sendFinal() {
		sendFinal(stream, getResponseTrailers());
		if (forceClose)
			connection.goway();
	}

	@Override
	protected void forceClose(Headers headers) {
		forceClose = (Boolean) processor.get(Parameter.HTTP2_ALLOW_FORCE_CLOSE);
	}

	private Function<Function<Integer, Boolean>, Consumer<Integer>> partitioner() {
		int partitionSize = connection.getMaxFrameSize();
		return partitionConsumer -> window -> {
			for (int partitions = window / partitionSize; partitions > 0; partitions--)
				if (!partitionConsumer.apply(partitionSize))
					return;
			int remaining = window % partitionSize;
			if (remaining > 0)
				partitionConsumer.apply(remaining);
		};
	}

	private static void sendFinal(Stream stream, ByteBuffer[] datas, Headers headers) {
		if (!headers.entrySet().isEmpty()) {
			stream.sendData(datas, false);
			stream.sendHeaders(transform(headers), true);
		} else
			stream.sendData(datas, true);
	}

	private static void sendFinal(Stream stream, Headers headers) {
		if (!headers.entrySet().isEmpty())
			stream.sendHeaders(transform(headers), true);
		else
			stream.sendData(EMPTY, true);
	}

	private static ByteBuffer frag(ByteBuffer data, int length) {
		ByteBuffer frag = data.duplicate();
		int position = data.position() + length;
		data.position(position);
		frag.limit(position);
		return frag;
	}

	private static class Http2Sender implements CustomSender {
		private final ApplicationExecutor executor;
		private final Stream stream;
		private final Consumer<Integer> consumer;
		private final Queue<ByteBuffer> vbuf = new ArrayDeque<>();
		private int fin = 0;

		Http2Sender(ApplicationExecutor executor, Stream stream, Runnable onSendReady,
				Function<Function<Integer, Boolean>, Consumer<Integer>> partitioner,
				Supplier<Headers> trailersSupplier) {
			this.executor = executor;
			this.stream = stream;
			this.consumer = partitioner.apply(window -> {
				ByteBuffer data = vbuf.peek();
				if (data == null) {
					if (fin == 1) {
						Http2Exchange.sendFinal(stream, trailersSupplier.get());
						fin = 2;
					}
					return false;
				}
				int remaining = data.remaining();
				if (remaining > window) {
					stream.sendData(frag(data, window), false);
					return true;
				}
				for (List<ByteBuffer> list = new ArrayList<>();;) {
					list.add(vbuf.poll());
					window -= remaining;
					if ((data = vbuf.peek()) == null) {
						ByteBuffer[] datas = list.toArray(new ByteBuffer[0]);
						if (fin == 0) {
							stream.sendData(datas, false);
							onSendReady.run();
						} else {
							Http2Exchange.sendFinal(stream, datas, trailersSupplier.get());
							fin = 2;
						}
						return false;
					}
					if ((remaining = data.remaining()) > window) {
						list.add(frag(data, window));
						stream.sendData(list.toArray(new ByteBuffer[0]), false);
						return true;
					}
				}
			});
			stream.setPump(() -> flush());
		}

		private void flush() {
			executor.executeExclusively(() -> {
				synchronized (vbuf) {
					consumer.accept(stream.getWindowSize());
				}
			});
		}

		@Override
		public void send(ByteBuffer bb) {
			if (!bb.hasRemaining())
				return;
			synchronized (vbuf) {
				if (fin != 0)
					return;
				boolean flush = vbuf.isEmpty();
				vbuf.add(bb);
				if (!flush)
					return;
			}
			flush();
		}

		@Override
		public void sendFinal(long timeout) {
			synchronized (vbuf) {
				if (fin != 0)
					return;
				fin = 1;
				if (!vbuf.isEmpty())
					return;
			}
			flush();
		}

		@Override
		public void cancel() {
			synchronized (vbuf) {
				vbuf.clear();
				fin = 2;
				stream.sendReset(ErrorCode.CANCEL);
			}
		}
	}

	@Override
	protected CustomSender createWebSocketSender(Runnable onSendReady) {
		return new Http2Sender(executor, stream, onSendReady, partitioner(), () -> EMPTY_TRAILERS);
	}

	@Override
	protected CustomSender createCustomSender(Runnable onSendReady) {
		return new Http2Sender(executor, stream, onSendReady, partitioner(), () -> getResponseTrailers());
	}
}
