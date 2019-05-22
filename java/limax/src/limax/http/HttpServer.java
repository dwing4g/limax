package limax.http;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;

import limax.net.io.NetModel;
import limax.net.io.NetTask;
import limax.net.io.ServerContext;
import limax.util.Closeable;

public class HttpServer extends Host {
	public enum Parameter {
		NETTASK_SENDBUF_SIZE(8192), NETTASK_RECVBUF_SIZE(8192), NETSERVER_BACKLOG(128), NETSERVER_WORKMODE(true),
		HTTP2_HEADER_TABLE_SIZE(4096), HTTP2_MAX_CONCURRENT_STREAMS(100), HTTP2_INITIAL_WINDOW_SIZE(65535),
		HTTP2_MAX_FRAME_SIZE(16384), HTTP2_MAX_HEADER_LIST_SIZE(-1), HTTP2_WINDOW_UPDATE_ACCUMULATE_PERCENT(80),
		HTTP2_SETTINGS_ENABLE_CONNECT_PROTOCOL(1), HTTP2_SETTINGS_TIMEOUT(5000L), HTTP2_CONNECTION_WINDOW_SIZE(65535),
		HTTP2_RTT_MEASURE_PERIOD(30000L), HTTP2_RTT_SAMPLES(5), HTTP2_PING_TIMEOUT(10000L),
		HTTP2_INCOMPLETE_FRAME_TIMEOUT(2000L), HTTP2_IDLE_TIMEOUT(20000L), HTTP2_ALLOW_FORCE_CLOSE(false),
		HTTP11_PARSER_LINE_CHARACTERS_MAX(16384), HTTP11_PARSER_HEADER_LINES_MAX(32), HTTP11_REQUEST_TIMEOUT(3000L),
		HTTP11_CLOSE_TIMEOUT(1000L), FLOWCONTROL_WINDOW_SIZE(65535), CONGESTION_TIMEOUT(5000L),
		WEBSOCKET_MAX_INCOMING_MESSAGE_SIZE(65536), WEBSOCKET_DEFAULT_FINAL_TIMEOUT(1000L),
		HANDLER_403(new HttpHandler() {
			@Override
			public DataSupplier handle(HttpExchange exchange) throws Exception {
				exchange.getResponseHeaders().set(":status", HttpURLConnection.HTTP_FORBIDDEN);
				return null;
			}
		}), HANDLER_404(new HttpHandler() {
			@Override
			public DataSupplier handle(HttpExchange exchange) throws Exception {
				exchange.getResponseHeaders().set(":status", HttpURLConnection.HTTP_NOT_FOUND);
				return null;
			}
		}), HANDLER_ASTERISK(new HttpHandler() {
			@Override
			public DataSupplier handle(HttpExchange exchange) throws Exception {
				return null;
			}
		});

		private final Object def;

		Parameter(Object def) {
			this.def = def;
		}

		public Object def() {
			return def;
		}
	}

	private final EnumMap<Parameter, Object> parameters = new EnumMap<>(Parameter.class);
	private final Map<String, Host> hosts = new ConcurrentHashMap<>();
	private final Set<Closeable> closeables = Collections.synchronizedSet(new HashSet<>());
	private final InetSocketAddress sa;
	private final SSLContext sslContext;
	private final int sslMode;
	private ServerContext serverContext;

	private HttpServer(InetSocketAddress sa, SSLContext sslContext, boolean needClientAuth, boolean wantClientAuth)
			throws IOException {
		super("__DEFAULT__");
		this.sa = sa;
		this.sslContext = sslContext;
		int sslMode = 0;
		if (needClientAuth)
			sslMode |= NetModel.SSL_NEED_CLIENT_AUTH;
		if (wantClientAuth)
			sslMode |= NetModel.SSL_WANT_CLIENT_AUTH;
		this.sslMode = sslMode;
		for (Parameter parameter : Parameter.values())
			parameters.put(parameter, parameter.def());
	}

	@Override
	public Object get(Parameter key) {
		Object value = super.get(key);
		return value != null ? value : parameters.get(key);
	}

	@Override
	public Object set(Parameter key, Object value) {
		Object previous = super.set(key, value);
		return previous != null ? previous : parameters.put(key, key.def().getClass().cast(value));
	}

	public synchronized void start() throws IOException {
		if (parameters.isEmpty())
			throw new UnsupportedOperationException("httpserver restart unsupported");
		if (this.serverContext != null)
			throw new IllegalStateException("httpserver already started");
		this.serverContext = NetModel.addServer(sa, (Integer) parameters.get(Parameter.NETSERVER_BACKLOG),
				(Integer) parameters.get(Parameter.NETTASK_RECVBUF_SIZE),
				(Integer) parameters.get(Parameter.NETTASK_SENDBUF_SIZE), sslContext, sslMode,
				new ServerContext.NetTaskConstructor() {
					@Override
					public NetTask newInstance(ServerContext context) {
						return NetModel.createServerTask(context,
								new HttpProcessor(parameters, HttpServer.this, hosts));
					}

					@Override
					public String getServiceName() {
						return "HttpServer:" + sa;
					}
				}, true, (Boolean) parameters.get(Parameter.NETSERVER_WORKMODE));
	}

	public synchronized void stop() throws IOException {
		if (parameters.isEmpty())
			throw new IllegalStateException("httpserver already stopped");
		serverContext.close();
		for (Closeable c : closeables)
			try {
				c.close();
			} catch (Throwable t) {
			}
		closeables.clear();
		parameters.clear();
	}

	public Host createHost(String dnsName) {
		return hosts.computeIfAbsent(normalizeDnsName(dnsName), name -> new Host(name));
	}

	public void removeHost(String dnsName) {
		hosts.remove(normalizeDnsName(dnsName));
	}

	public void register(Closeable closeable) {
		closeables.add(closeable);
	}

	public HttpHandler createFileSystemHandler(Path htdocs, String textCharset, int mmapThreshold,
			double compressThreshold, String[] indexes, boolean browseDir, String[] browseDirExceptions)
			throws IOException {
		FileSystemHandler handler = new FileSystemHandler(htdocs, textCharset, mmapThreshold, compressThreshold,
				indexes, browseDir, browseDirExceptions);
		register(handler);
		return handler;
	}

	public static HttpServer create(InetSocketAddress sa) throws IOException {
		return new HttpServer(sa, null, false, false);
	}

	public static HttpServer create(InetSocketAddress sa, SSLContext sslContext) throws IOException {
		return new HttpServer(sa, sslContext, false, false);
	}

	public static HttpServer create(InetSocketAddress sa, SSLContext sslContext, boolean needClientAuth,
			boolean wantClientAuth) throws IOException {
		return new HttpServer(sa, sslContext, needClientAuth, wantClientAuth);
	}
}
