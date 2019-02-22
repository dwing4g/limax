package limax.http;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import limax.http.RFC7540.Connection;
import limax.http.RFC7540.Processor;
import limax.http.RFC7540.Stream;
import limax.http.RFC7541.Entry;

class Http2Exchange extends AbstractHttpExchange implements Processor {
	private final static ByteBuffer EMPTY = ByteBuffer.allocate(0);
	private final Connection connection;
	private final Stream stream;
	private Headers headers;
	private Headers trailers;
	private boolean forceClose;

	Http2Exchange(HttpProcessor processor, Connection connection, Stream stream, Headers headers) {
		super(processor);
		this.connection = connection;
		this.stream = stream;
		if (headers != null) {
			this.headers = headers;
			requestFinished();
		}
	}

	@Override
	public void process(List<Entry> headers) {
		Headers tmp = new Headers();
		headers.forEach(e -> tmp.add(e.getKey(), e.getValue()));
		if (this.headers == null)
			httpHandler = (HttpHandler) getHandler(this.headers = tmp);
		if (this.trailers == null)
			this.trailers = tmp;
	}

	@Override
	public void process(ByteBuffer in) {
		if (formData == null)
			formData = createFormData(headers);
		while (in.hasRemaining())
			formData.process(in.get());
		schedule(false);
	}

	@Override
	public void requestFinished() {
		if (formData == null)
			formData = new FormData(getRequestURI().getQuery());
		formData.end();
		schedule(true);
	}

	@Override
	public void shutdown(Throwable closeReason) {
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

	private static List<Entry> transform(Headers headers) {
		List<Entry> r = new ArrayList<>();
		for (Map.Entry<String, List<String>> e : headers.entrySet())
			for (String value : e.getValue())
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
		headers.entrySet().remove("connection");
		stream.sendHeaders(transform(headers), !body);
		return body;
	}

	private Consumer<Integer> windowConsumer;

	@Override
	protected Runnable[] flowControl(Consumer<Integer> windowConsumer) {
		this.windowConsumer = windowConsumer;
		return new Runnable[] { () -> update(stream.getWindowSize()), () -> {
		} };
	}

	@Override
	public void update(int windowSize) {
		windowConsumer.accept(windowSize);
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
	protected void sendFinal(ByteBuffer data) {
		Headers headers = getResponseTrailers();
		if (headers.entrySet().size() > 0) {
			stream.sendData(data, false);
			stream.sendHeaders(transform(headers), true);
		} else
			stream.sendData(data, true);
		if (forceClose)
			connection.goway();
	}

	@Override
	protected void sendFinal(ByteBuffer[] datas) {
		Headers headers = getResponseTrailers();
		if (headers.entrySet().size() > 0) {
			stream.sendData(datas, false);
			stream.sendHeaders(transform(headers), true);
		} else
			stream.sendData(datas, true);
		if (forceClose)
			connection.goway();
	}

	@Override
	protected void sendFinal() {
		sendFinal(EMPTY);
	}

	@Override
	protected void forceClose() {
		forceClose = true;
	}
}
