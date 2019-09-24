package limax.http;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.net.ssl.SSLSession;

abstract class AbstractHttpExchange implements HttpExchange {
	final HttpProcessor processor;
	FormData formData;
	HttpHandler httpHandler;
	boolean forceClose;
	private final Headers headers = new Headers();
	private final Headers trailers = new Headers();
	private Host host;
	private URI contextURI;
	private URI requestURI;
	private SendJob sendJob;

	AbstractHttpExchange(HttpProcessor processor) {
		this.processor = processor;
	}

	abstract boolean sendResponseHeaders(Headers headers, Long length);

	abstract void flowControl(Function<Integer, Boolean> windowConsumer);

	abstract void send(ByteBuffer data, boolean chunk);

	abstract void send(ByteBuffer[] datas, boolean chunk);

	abstract void sendFinal(ByteBuffer[] data);

	abstract void sendFinal();

	abstract void forceClose(Headers headers);

	abstract CustomSender createWebSocketSender(Runnable onSendReady);

	abstract CustomSender createCustomSender(Runnable onSendReady, Runnable onClose);

	interface SendJob {
		void launch(AbstractHttpExchange exchange);

		void cancel();
	}

	static class CustomDataSupplier implements DataSupplier, SendJob {
		private final Consumer<CustomSender> consumer;
		private final Runnable onSendReady;
		private final Runnable onClose;

		@Override
		public void launch(AbstractHttpExchange exchange) {
			consumer.accept(exchange.createCustomSender(onSendReady, onClose));
			onSendReady.run();
		}

		@Override
		public void cancel() {
			onClose.run();
		}

		CustomDataSupplier(Consumer<CustomSender> consumer, Runnable onSendReady, Runnable onClose) {
			this.consumer = consumer;
			this.onSendReady = onSendReady;
			AtomicBoolean once = new AtomicBoolean();
			this.onClose = () -> {
				if (once.compareAndSet(false, true))
					onClose.run();
			};
		}

		@Override
		public ByteBuffer get() throws Exception {
			throw new UnsupportedOperationException();
		}
	}

	private abstract class SendLoop implements Function<Integer, Boolean>, SendJob {
		private final DataSupplier dataSupplier;
		ByteBuffer data;
		private boolean done;

		SendLoop(DataSupplier dataSupplier) {
			this.dataSupplier = dataSupplier;
		}

		ByteBuffer frag(int length) {
			ByteBuffer frag = data.duplicate();
			int position = data.position() + length;
			data.position(position);
			frag.limit(position);
			return frag;
		}

		boolean load() {
			try {
				return (data = dataSupplier.get()) != null;
			} catch (Exception e) {
				return false;
			}
		}

		abstract void fill(int window);

		void done() {
			done = true;
			try {
				dataSupplier.done(AbstractHttpExchange.this);
			} catch (Throwable t) {
			}
		}

		@Override
		public Boolean apply(Integer window) {
			if (!done)
				fill(window);
			return !done;
		}

		@Override
		public void launch(AbstractHttpExchange exchange) {
			exchange.flowControl(this);
		}

		@Override
		public void cancel() {
			if (!done)
				done();
		}
	}

	private class DeterministicSendLoop extends SendLoop {
		DeterministicSendLoop(DataSupplier dataSupplier) {
			super(dataSupplier);
			load();
		}

		@Override
		void fill(int window) {
			int remaining = data.remaining();
			if (remaining > window) {
				send(frag(window), false);
				return;
			}
			for (List<ByteBuffer> list = new ArrayList<>();;) {
				list.add(data);
				window -= remaining;
				if (!load()) {
					sendFinal(list.toArray(new ByteBuffer[0]));
					done();
					return;
				}
				if ((remaining = data.remaining()) > window) {
					list.add(frag(window));
					send(list.toArray(new ByteBuffer[0]), false);
					return;
				}
			}
		}
	}

	private class UndeterministicSendLoop extends SendLoop {
		UndeterministicSendLoop(DataSupplier dataSupplier) {
			super(dataSupplier);
		}

		@Override
		void fill(int window) {
			if (data == null && !load()) {
				sendFinal();
				return;
			}
			int remaining = data.remaining();
			if (remaining > window) {
				send(frag(window), true);
				return;
			}
			for (List<ByteBuffer> list = new ArrayList<>();;) {
				list.add(data);
				window -= remaining;
				if (!load()) {
					send(list.toArray(new ByteBuffer[0]), true);
					sendFinal();
					return;
				}
				if ((remaining = data.remaining()) > window) {
					list.add(frag(window));
					send(list.toArray(new ByteBuffer[0]), true);
					return;
				}
			}
		}

		private void sendFinal() {
			done();
			AbstractHttpExchange.this.sendFinal();
		}
	}

	private FormData getAndResetFormData() {
		FormData formData = this.formData;
		this.formData = null;
		return formData;
	}

	void cancel() {
		FormData formData = getAndResetFormData();
		if (formData != null)
			formData.end(true);
		processor.execute(() -> {
			if (sendJob != null)
				sendJob.cancel();
		});
	}

	@Override
	public void async(HttpHandler handler) {
		processor.execute(() -> {
			DataSupplier dataSupplier;
			try {
				try {
					dataSupplier = handler.handle(this);
				} catch (HttpException e) {
					headers.entrySet().clear();
					if (e.isForceClose())
						forceClose(headers);
					dataSupplier = e.getHandler().handle(this);
				}
			} catch (Throwable t) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try (PrintStream ps = new PrintStream(baos, false, "utf-8")) {
					t.printStackTrace(ps);
				} catch (UnsupportedEncodingException e1) {
				}
				headers.entrySet().clear();
				forceClose(headers);
				headers.set(":status", HttpURLConnection.HTTP_INTERNAL_ERROR);
				headers.set("Content-Type", "text/plain; charset=utf-8");
				dataSupplier = DataSupplier.from(baos.toByteArray());
			}
			if (dataSupplier instanceof AsyncDataSupplier || getAndResetFormData() == null)
				return;
			if (headers.get(":status") == null)
				headers.set(":status", 200);
			headers.set("date", ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME));
			headers.set("server", "Limax/1.1");
			Long length;
			if (dataSupplier == null)
				length = 0L;
			else if (dataSupplier instanceof DeterministicDataSupplier)
				length = ((DeterministicDataSupplier) dataSupplier).getLength();
			else
				length = null;
			if (sendResponseHeaders(headers, length)) {
				sendJob = length == null
						? dataSupplier instanceof SendJob ? (SendJob) dataSupplier
								: new UndeterministicSendLoop(dataSupplier)
						: new DeterministicSendLoop(dataSupplier);
				sendJob.launch(this);
			}
		});
	}

	Handler find(Headers headers) {
		String dnsName = headers.getFirst(":authority");
		if (dnsName == null)
			dnsName = headers.getFirst("host");
		host = processor.find(dnsName);
		requestURI = URI.create(headers.getFirst(":path")).normalize();
		HttpContext ctx = host.find(requestURI.getPath());
		contextURI = ctx.uri();
		return ctx.handler();
	}

	@Override
	public InetSocketAddress getLocalAddress() {
		return processor.getLocalAddress();
	}

	@Override
	public InetSocketAddress getPeerAddress() {
		return processor.getPeerAddress();
	}

	@Override
	public Host getHost() {
		return host;
	}

	@Override
	public URI getContextURI() {
		return contextURI;
	}

	@Override
	public URI getRequestURI() {
		return requestURI;
	}

	@Override
	public String getRequestMethod() {
		return getRequestHeaders().getFirst(":method");
	}

	@Override
	public FormData getFormData() {
		return formData;
	}

	@Override
	public Headers getResponseHeaders() {
		return headers;
	}

	@Override
	public Headers getResponseTrailers() {
		return trailers;
	}

	@Override
	public SSLSession getSSLSession() {
		return processor.nettask().getSSLSession();
	}
}
