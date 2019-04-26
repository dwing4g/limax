package limax.http;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.function.Consumer;
import java.util.function.Function;

abstract class AbstractHttpExchange implements HttpExchange {
	private final static String pattern = "EEE, dd MMM yyyy HH:mm:ss zzz";
	private final static TimeZone gmtTZ = TimeZone.getTimeZone("GMT");
	private final static ThreadLocal<DateFormat> dateFormat = ThreadLocal.withInitial(() -> {
		DateFormat df = new SimpleDateFormat(pattern, Locale.US);
		df.setTimeZone(gmtTZ);
		return df;
	});

	protected final ApplicationExecutor executor;
	protected final HttpProcessor processor;
	private final Headers headers = new Headers();
	private final Headers trailers = new Headers();
	private Host host;
	private URI contextURI;
	private URI requestURI;
	protected FormData formData;
	protected HttpHandler httpHandler;
	private boolean requestFinished;
	private SendLoop sendLoop;
	private Exception abort;

	AbstractHttpExchange(ApplicationExecutor executor, HttpProcessor processor) {
		this.executor = executor;
		this.processor = processor;
	}

	protected abstract boolean sendResponseHeaders(Headers headers, Long length);

	protected abstract void flowControl(Function<Integer, Boolean> windowConsumer);

	protected abstract void send(ByteBuffer data, boolean chunk);

	protected abstract void send(ByteBuffer[] datas, boolean chunk);

	protected abstract void sendFinal(ByteBuffer[] data);

	protected abstract void sendFinal();

	protected abstract void forceClose(Headers headers);

	protected abstract CustomSender createWebSocketSender(Runnable onSendReady);

	protected abstract CustomSender createCustomSender(Runnable onSendReady);

	DataSupplier createCustomDataSupplier(Consumer<CustomSender> consumer, Runnable onSendReady) {
		executor.execute(() -> consumer.accept(createCustomSender(onSendReady)));
		return new CustomDataSupplier();
	}

	private abstract class SendLoop implements Function<Integer, Boolean> {
		private final DataSupplier dataSupplier;
		protected ByteBuffer data;

		SendLoop(DataSupplier dataSupplier) {
			this.dataSupplier = dataSupplier;
			sendLoop = this;
		}

		ByteBuffer frag(int length) {
			ByteBuffer frag = data.duplicate();
			int position = data.position() + length;
			data.position(position);
			frag.limit(position);
			return frag;
		}

		boolean load() throws Exception {
			return (data = dataSupplier.get()) != null;
		}

		@Override
		public Boolean apply(Integer window) {
			if (sendLoop != null)
				fill(window);
			return sendLoop != null;
		}

		abstract void fill(int window);

		abstract void cleanup();

		void done(boolean cancel) {
			if (sendLoop != null) {
				sendLoop = null;
				try {
					dataSupplier.done(AbstractHttpExchange.this);
				} catch (Throwable t) {
				}
				if (!cancel)
					cleanup();
			}
		}
	}

	private class DeterministicSendLoop extends SendLoop {
		DeterministicSendLoop(DataSupplier dataSupplier) throws Exception {
			super(dataSupplier);
			load();
		}

		@Override
		void fill(int window) {
			try {
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
						done(false);
						return;
					}
					if ((remaining = data.remaining()) > window) {
						list.add(frag(window));
						send(list.toArray(new ByteBuffer[0]), false);
						return;
					}
				}
			} catch (Exception e) {
			}
		}

		@Override
		void cleanup() {
		}
	}

	private class UndeterministicSendLoop extends SendLoop {
		UndeterministicSendLoop(DataSupplier dataSupplier) {
			super(dataSupplier);
		}

		@Override
		void fill(int window) {
			try {
				if (data == null && !load()) {
					done(false);
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
						done(false);
						return;
					}
					if ((remaining = data.remaining()) > window) {
						list.add(frag(window));
						send(list.toArray(new ByteBuffer[0]), true);
						return;
					}
				}
			} catch (Exception e) {
				done(false);
			}
		}

		@Override
		void cleanup() {
			sendFinal();
		}
	}

	private void response(Headers headers, DataSupplier dataSupplier) throws Exception {
		if (dataSupplier instanceof AsyncDataSupplier || formData == null)
			return;
		formData = null;
		if (headers.get(":status") == null)
			headers.set(":status", 200);
		headers.set("date", dateFormat.get().format(new Date()));
		headers.set("server", "Limax/1.0");
		Long length;
		if (dataSupplier == null)
			length = 0L;
		else if (dataSupplier instanceof DeterministicDataSupplier)
			length = ((DeterministicDataSupplier) dataSupplier).getLength();
		else
			length = null;
		if (sendResponseHeaders(headers, length)) {
			if (length != null)
				flowControl(new DeterministicSendLoop(dataSupplier));
			else if (!(dataSupplier instanceof CustomDataSupplier))
				flowControl(new UndeterministicSendLoop(dataSupplier));
		}
	}

	@Override
	public void async(DataSupplier dataSupplier) {
		executor.execute(() -> {
			try {
				response(headers, dataSupplier);
			} catch (Throwable t) {
				response500(t);
			}
		});
	}

	private void response500(Throwable t) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (PrintStream ps = new PrintStream(baos, false, "utf-8")) {
			t.printStackTrace(ps);
		} catch (UnsupportedEncodingException e1) {
		}
		headers.entrySet().clear();
		forceClose(headers);
		headers.set(":status", HttpURLConnection.HTTP_INTERNAL_ERROR);
		headers.set("Content-Type", "text/plain; charset=utf-8");
		try {
			response(headers, DataSupplier.from(baos.toByteArray()));
		} catch (Exception e) {
		}
	}

	private Runnable createTask(boolean requestFinished) {
		return () -> {
			try {
				try {
					if (requestFinished)
						if (abort == null) {
							this.requestFinished = requestFinished;
							response(headers, httpHandler.handle(this));
						} else
							throw abort;
					else if (abort == null)
						try {
							httpHandler.handle(this);
						} catch (Exception e) {
							abort = e;
						}
				} catch (HttpException e) {
					headers.entrySet().clear();
					if (e.isForceClose())
						forceClose(headers);
					response(headers, e.getHandler().handle(this));
				}
			} catch (Throwable t) {
				response500(t);
			}
		};
	}

	protected void schedule(boolean requestFinished) {
		if (requestFinished)
			executor.execute(createTask(requestFinished));
		else if (abort == null)
			executor.executeExclusively(createTask(requestFinished));
	}

	protected void cancel() {
		if (formData != null)
			formData.end(true);
		executor.execute(() -> {
			if (sendLoop != null)
				sendLoop.done(true);
		});
	}

	protected Handler find(Headers headers) {
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
	public boolean isRequestFinished() {
		return requestFinished;
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
}
