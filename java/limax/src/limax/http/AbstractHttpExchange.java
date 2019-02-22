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

import limax.net.Engine;

abstract class AbstractHttpExchange implements HttpExchange {
	private final static String pattern = "EEE, dd MMM yyyy HH:mm:ss zzz";
	private final static TimeZone gmtTZ = TimeZone.getTimeZone("GMT");
	private final static ThreadLocal<DateFormat> dateFormat = ThreadLocal.withInitial(() -> {
		DateFormat df = new SimpleDateFormat(pattern, Locale.US);
		df.setTimeZone(gmtTZ);
		return df;
	});

	protected final HttpProcessor processor;
	private final Headers headers = new Headers();
	private final Headers otrailers = new Headers();
	protected FormData formData;
	protected HttpHandler httpHandler;
	private boolean requestFinished;
	private SendLoop sendLoop;

	AbstractHttpExchange(HttpProcessor processor) {
		this.processor = processor;
	}

	protected abstract boolean sendResponseHeaders(Headers headers, Long length);

	protected abstract Runnable[] flowControl(Consumer<Integer> windowConsumer);

	protected abstract void send(ByteBuffer data, boolean chunk);

	protected abstract void send(ByteBuffer[] datas, boolean chunk);

	protected abstract void sendFinal(ByteBuffer data);

	protected abstract void sendFinal(ByteBuffer[] data);

	protected abstract void sendFinal();

	protected abstract void forceClose();

	private abstract class SendLoop implements Consumer<Integer> {
		private final Runnable[] actions;
		private final DataSupplier dataSupplier;
		protected ByteBuffer data;

		SendLoop(DataSupplier dataSupplier) {
			this.dataSupplier = dataSupplier;
			this.actions = flowControl(this);
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
		public void accept(Integer window) {
			if (sendLoop != null)
				fill(window);
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
				if (!cancel) {
					actions[1].run();
					cleanup();
				}
			}
		}

		void startup() {
			actions[0].run();
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
		if (headers.get(":status") == null)
			headers.set(":status", 200);
		headers.set("date", dateFormat.get().format(new Date()));
		headers.set("server", "Limax/httpserver");
		Long length;
		if (dataSupplier == null)
			length = 0L;
		else if (dataSupplier instanceof DeterministicDataSupplier)
			length = ((DeterministicDataSupplier) dataSupplier).getLength();
		else
			length = null;
		if (sendResponseHeaders(headers, length)) {
			sendLoop = length != null ? new DeterministicSendLoop(dataSupplier)
					: new UndeterministicSendLoop(dataSupplier);
			sendLoop.startup();
		}
	}

	private Runnable createTask(boolean requestFinished) {
		return () -> {
			try {
				try {
					this.requestFinished = requestFinished;
					DataSupplier dataSupplier = httpHandler.handle(this);
					if (requestFinished)
						response(headers, dataSupplier);
				} catch (HttpException e) {
					if (e.isForceClose())
						forceClose();
					headers.entrySet().clear();
					response(headers, e.getHandler().handle(this));
				}
			} catch (Throwable e) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try (PrintStream ps = new PrintStream(baos, false, "utf-8")) {
					e.printStackTrace(ps);
				} catch (UnsupportedEncodingException e1) {
				}
				forceClose();
				headers.entrySet().clear();
				headers.set(":status", HttpURLConnection.HTTP_INTERNAL_ERROR);
				headers.set("Content-Type", "text/plain; charset=utf-8");
				ByteBuffer data = ByteBuffer.wrap(baos.toByteArray());
				sendResponseHeaders(headers, (long) data.remaining());
				sendFinal(data);
			}
		};
	}

	protected void schedule(boolean requestFinished) {
		if (requestFinished)
			Engine.getApplicationExecutor().execute(this, createTask(requestFinished));
		else
			Engine.getApplicationExecutor().schedule(this, createTask(requestFinished));
	}

	protected void cancel() {
		Engine.getApplicationExecutor().execute(this, () -> {
			if (sendLoop != null)
				sendLoop.done(true);
			if (formData != null)
				formData.end();
		});
	}

	protected Handler getHandler(Headers headers) {
		return processor.getHandler(headers.getFirst("host"), headers.getFirst(":path"));
	}

	protected FormData createFormData(Headers headers) {
		boolean multipart;
		try {
			multipart = headers.getFirst("content-type").contains("boundary=");
		} catch (Exception e) {
			multipart = false;
		}
		return new FormData(multipart);
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
	public URI getRequestURI() {
		return URI.create(getRequestHeaders().getFirst(":path"));
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
		return otrailers;
	}
}
