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

	Http2Exchange(HttpProcessor processor, Connection connection, Stream stream, Headers headers) {
		super(processor);
		this.connection = connection;
		this.stream = stream;
		if (headers != null) {
			process(headers);
			end();
		}
	}

	@Override
	public Headers getRequestHeaders() {
		return headers;
	}

	@Override
	public Headers getRequestTrailers() {
		return trailers;
	}

	private void process(Headers headers) {
		if (this.headers == null) {
			Handler handler = find(this.headers = headers);
			if (handler instanceof HttpHandler)
				httpHandler = (HttpHandler) handler;
			else
				processor.execute(() -> {
					stream.startup(new RFC8441((WebSocketHandler) handler, this, stream));
					stream.sendHeaders(Collections.singletonList(new Entry(":status", "200")), false);
				});
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
	public void process(ByteBuffer in) throws Exception {
		if (formData == null)
			formData = new FormData(headers);
		int remaining = in.remaining();
		for (int i = 0; i < remaining; i++)
			formData.process(in.get());
		stream.windowUpdate(remaining);
		httpHandler.censor(this);
	}

	@Override
	public void end() {
		if (formData == null)
			formData = new FormData(getRequestURI());
		formData.end(false);
		async(httpHandler);
	}

	@Override
	public void cancel(Throwable closeReason) {
		processor.nettask().cancel(closeReason);
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
	boolean sendResponseHeaders(Headers headers, Long length) {
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
	void flowControl(Function<Integer, Boolean> windowConsumer) {
		Consumer<Integer> partitionConsumer = partitioner().apply(windowConsumer);
		stream.setPump(window -> partitionConsumer.accept(window));
	}

	@Override
	void send(ByteBuffer data, boolean chunk) {
		stream.sendData(data, false);
	}

	@Override
	void send(ByteBuffer[] datas, boolean chunk) {
		stream.sendData(datas, false);
	}

	@Override
	void sendFinal(ByteBuffer[] datas) {
		sendFinal(stream, datas, getResponseTrailers());
		if (forceClose)
			connection.goway();
	}

	@Override
	void sendFinal() {
		sendFinal(stream, getResponseTrailers());
		if (forceClose)
			connection.goway();
	}

	@Override
	void forceClose(Headers headers) {
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

	private static class Http2Sender implements CustomSender {
		private final Stream stream;
		private final Consumer<Integer> consumer;
		private final Queue<ByteBuffer> vbuf = new ArrayDeque<>();
		private int fin = 0;

		private static ByteBuffer frag(ByteBuffer data, int length) {
			ByteBuffer frag = data.duplicate();
			int position = data.position() + length;
			data.position(position);
			frag.limit(position);
			return frag;
		}

		Http2Sender(Stream stream, Runnable onSendReady, Runnable onClose,
				Function<Function<Integer, Boolean>, Consumer<Integer>> partitioner,
				Supplier<Headers> trailersSupplier) {
			this.stream = stream;
			this.consumer = partitioner.apply(window -> {
				ByteBuffer data = vbuf.peek();
				if (data == null) {
					if (fin == 1) {
						Http2Exchange.sendFinal(stream, trailersSupplier.get());
						fin = 2;
						if (onClose != null)
							onClose.run();
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
							if (onClose != null)
								onClose.run();
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
			stream.setPump(window -> consumer.accept(window));
		}

		@Override
		public void send(ByteBuffer bb) {
			if (!bb.hasRemaining())
				return;
			stream.flush(() -> {
				if (fin != 0)
					return false;
				vbuf.add(bb);
				return true;
			});
		}

		@Override
		public void sendFinal(long timeout) {
			stream.flush(() -> {
				if (fin != 0)
					return false;
				fin = 1;
				return true;
			});
		}
	}

	@Override
	CustomSender createWebSocketSender(Runnable onSendReady) {
		return new Http2Sender(stream, onSendReady, null, partitioner(), () -> EMPTY_TRAILERS);
	}

	@Override
	CustomSender createCustomSender(Runnable onSendReady, Runnable onClose) {
		return new Http2Sender(stream, onSendReady, onClose, partitioner(), () -> getResponseTrailers());
	}
}
