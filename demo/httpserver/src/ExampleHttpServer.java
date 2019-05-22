import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;

import limax.codec.Octets;
import limax.codec.SinkOctets;
import limax.codec.StreamSource;
import limax.http.DataSupplier;
import limax.http.FormData;
import limax.http.HttpExchange;
import limax.http.HttpHandler;
import limax.http.HttpServer;
import limax.http.WebSocketExchange;
import limax.net.Engine;
import limax.pkix.KeyInfo;
import limax.util.Helper;
import limax.util.Pair;

public class ExampleHttpServer {
	private final static String uploadHtml = "<html><body><form action=\"upload\" method=\"post\" enctype=\"multipart/form-data\" accept-charset=\"utf-8\"><textarea name=\"text\"></textarea><textarea name=\"text\"></textarea><input type=\"file\" name=\"upload\"><input type=\"submit\" value=\"OK\"></form></body></html>\r\n";
	private final static String uploadHtml2 = "<html><body><form action=\"upload2\" method=\"post\" enctype=\"multipart/form-data\" accept-charset=\"utf-8\">maxSize<input name=\"text\" onchange=\"javascript:this.parentElement.action='upload2/'+this.value\"></input><input type=\"file\" name=\"upload\"><input type=\"submit\" value=\"OK\"></form></body></html>\r\n";
	private final static URI uriP12 = URI.create("pkcs12:/work/localhost.p12");
	private final static Path htdocs = Paths.get("/work/httpserver/htdocs");
	private final static String[] indexes = new String[] { "index.htm", "index.html" };
	private final static boolean browseDir = true;
	private final static String[] browseDirExceptions = null;
	private final static int LIMIT = 65536;
	private final static long UPLOAD_MAX = Integer.MAX_VALUE;

	public static void main(String[] args) throws Exception {
		SSLContext sslContext = uriP12 == null ? null
				: KeyInfo.load(uriP12, prompt -> "123456".toCharArray()).createSSLContext(null, false, null);
		Engine.open(4, 16, 16);
		HttpServer httpServer = HttpServer.create(new InetSocketAddress(sslContext == null ? 80 : 443), sslContext);
		Octets data = new Octets();
		new StreamSource(ExampleHttpServer.class.getResourceAsStream("websocket.html"), new SinkOctets(data)).flush();
		byte[] wsHtml = sslContext == null ? data.getBytes()
				: new String(data.getBytes(), StandardCharsets.UTF_8).replace("ws://", "wss://")
						.getBytes(StandardCharsets.UTF_8);
		httpServer.createContext("/", httpServer.createFileSystemHandler(htdocs, "utf-8", 65536, 0.8, indexes,
				browseDir, browseDirExceptions));
		httpServer.createContext("/upload.html", exchange -> {
			exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
			return DataSupplier.from(uploadHtml, StandardCharsets.UTF_8);
		});
		httpServer.createContext("/upload2.html", exchange -> {
			exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
			return DataSupplier.from(uploadHtml2, StandardCharsets.UTF_8);
		});
		httpServer.createContext("/upload", new HttpHandler() {
			@Override
			public void censor(HttpExchange exchange) {
				exchange.getFormData().useTempFile(LIMIT).postLimit(UPLOAD_MAX);
			}

			@Override
			public DataSupplier handle(HttpExchange exchange) throws Exception {
				FormData formData = exchange.getFormData();
				exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
				StringBuilder sb = new StringBuilder();
				exchange.getRequestHeaders().entrySet()
						.forEach(e -> e.getValue().forEach(v -> sb.append(e.getKey() + ": " + v + "\r\n")));
				sb.append(formData.getData().toString());
				return DataSupplier.from(sb.toString(), StandardCharsets.UTF_8);
			}
		});
		httpServer.createContext("/upload2", new HttpHandler() {
			@Override
			public void censor(HttpExchange exchange) {
				FormData formData = exchange.getFormData();
				formData.getData().computeIfAbsent("_tmp_", k -> {
					formData.useTempFile(LIMIT).postLimit(Long.parseLong(exchange.getContextURI()
							.relativize(URI.create(exchange.getRequestURI().toString())).toString()));
					return Collections.singletonList(true);
				});
			}

			@Override
			public DataSupplier handle(HttpExchange exchange) throws Exception {
				FormData formData = exchange.getFormData();
				exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
				StringBuilder sb = new StringBuilder();
				exchange.getRequestHeaders().entrySet()
						.forEach(e -> e.getValue().forEach(v -> sb.append(e.getKey() + ": " + v + "\r\n")));
				sb.append(formData.getData().toString());
				return DataSupplier.from(sb.toString(), StandardCharsets.UTF_8);
			}
		});

		httpServer.createContext("/websocket.html", exchange -> {
			exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
			return DataSupplier.from(wsHtml);
		});
		httpServer.createContext("/websocket", e -> {
			WebSocketExchange exchange = e.getWebSocketExchange();
			switch (e.type()) {
			case OPEN:
				exchange.setSessionObject(Engine.getProtocolScheduler().scheduleAtFixedRate(() -> {
					exchange.send(exchange.getPeerAddress() + ": " + new Date());
					System.out.println("ping = " + exchange.ping());
				}, 1, 1, TimeUnit.SECONDS));
				return;
			case CLOSE: {
				Pair<Short, String> pair = e.getClose();
				System.out.println("code = " + pair.getKey() + " reason = " + pair.getValue());
				((Future<?>) exchange.getSessionObject()).cancel(false);
				return;
			}
			case SENDREADY:
				System.out.println("sendready");
				return;
			case TEXT:
				System.out.println("text: " + e.getText());
				return;
			case BINARY:
				System.out.println("binary: " + Helper.toHexString(e.getBinary()));
				return;
			case PONG: {
				Pair<Long, Long> pair = e.getPong();
				System.out.println("pong: " + pair.getKey() + " RTT:" + pair.getValue());
				return;
			}
			}
		});
		httpServer.createContext("/sse.html", exchange -> {
			exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
			return DataSupplier.from(ExampleHttpServer.class.getResourceAsStream("sse.html"), 4096);
		});
		httpServer.createContext("/sse", new HttpHandler() {
			@Override
			public DataSupplier handle(HttpExchange exchange) throws Exception {
				AtomicInteger count = new AtomicInteger(5);
				AtomicReference<Future<?>> ref = new AtomicReference<>();
				Random rand = new Random();
				return DataSupplier.from(exchange, (lastEventId, sse) -> {
					System.out.println("lastEventId:" + lastEventId);
					ref.set(Engine.getProtocolScheduler().scheduleAtFixedRate(() -> {
						if (count.decrementAndGet() == 0) {
							ref.get().cancel(false);
							sse.done();
						} else
							sse.emit(null, Integer.toHexString(rand.nextInt()), new Date().toString());
					}, 0, 1, TimeUnit.SECONDS));
				}, () -> System.out.println("sendready"), () -> System.out.println("close"));
			}
		});
		httpServer.createContext("/exception", new HttpHandler() {
			@Override
			public DataSupplier handle(HttpExchange exchange) throws Exception {
				throw new Exception("exception test");
			}
		});
		httpServer.createContext("/async", exchange -> {
			new Thread(() -> 
				exchange.async(_e -> {
					exchange.getResponseHeaders().set("Content-Type", "text/plain");
					return DataSupplier.from("async", StandardCharsets.ISO_8859_1);
				})).start();
			return DataSupplier.async();
		});
		httpServer.start();
	}
}
