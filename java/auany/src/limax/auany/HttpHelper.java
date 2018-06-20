package limax.auany;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import limax.auany.appconfig.AppKey;
import limax.auany.appconfig.ServiceType;
import limax.codec.CharSource;
import limax.codec.CodecException;
import limax.codec.JSON;
import limax.codec.JSONEncoder;
import limax.codec.JSONSerializable;
import limax.codec.MD5;
import limax.codec.SinkStream;

public final class HttpHelper {
	private HttpHelper() {
	}

	private static void responseHeaders(HttpExchange exchange, Headers responseHeaders, int arg0, int arg1)
			throws IOException {
		responseHeaders.set("Connection", "close");
		responseHeaders.set("Access-Control-Allow-Origin", "*");
		exchange.sendResponseHeaders(arg0, arg1);
	}

	public static HttpHandler createGetHandler(Cache cache) {
		return exchange -> {
			Headers responseHeaders = exchange.getResponseHeaders();
			try (OutputStream os = exchange.getResponseBody()) {
				cache.run(exchange.getRequestURI(), exchange.getRequestHeaders(), responseHeaders,
						() -> responseHeaders(exchange, responseHeaders, HttpURLConnection.HTTP_NOT_MODIFIED, -1),
						(len, osconsumer) -> {
							responseHeaders.putIfAbsent("Content-Type",
									Arrays.asList("application/json; charset=utf-8"));
							responseHeaders(exchange, responseHeaders, HttpURLConnection.HTTP_OK, len);
							osconsumer.accept(os);
						});
			} catch (Exception e) {
				responseHeaders(exchange, responseHeaders, HttpURLConnection.HTTP_BAD_REQUEST, -1);
			}
		};
	}

	public static Function<URI, AppKey> uri2AppKey(String context) {
		return uri -> {
			if (!uri.getPath().equals(context))
				return null;
			String query = uri.getQuery();
			int p0 = query.indexOf("=");
			ServiceType serviceType = ServiceType.valueOf(query.substring(0, p0++).toUpperCase());
			int p1 = query.indexOf('&', p0);
			return new AppKey(serviceType, Integer.parseInt(p1 == -1 ? query.substring(p0) : query.substring(p0, p1)));
		};
	}

	public static Function<JSONSerializable, byte[]> encodeJSON(Charset charset) {
		return json -> {
			try {
				return JSON.stringify(json).getBytes(charset);
			} catch (CodecException e) {
				throw new RuntimeException(e);
			}
		};
	}

	public static <K> Cache makeJSONCacheNone(Function<URI, K> uri2key, Function<K, JSONSerializable> jsonloader) {
		Function<URI, JSONSerializable> loader = uri2key.andThen(jsonloader);
		return (uri, requestHeaders, responseHeaders, match, output) -> {
			JSONSerializable json = loader.apply(uri);
			responseHeaders.add("Cache-control", "no-cache");
			output.accept(0,
					os -> JSONEncoder.encode(json, new CharSource(StandardCharsets.UTF_8, new SinkStream(os))));
		};
	}

	public static <K> Cache makeJSONCache(Function<URI, K> uri2key, Function<K, JSONSerializable> jsonloader) {
		return new ETagCache<K>(uri2key, jsonloader.andThen(encodeJSON(StandardCharsets.UTF_8)));
	}

	public interface Cache {
		@FunctionalInterface
		interface NotModified {
			void run() throws IOException;
		}

		@FunctionalInterface
		interface OutputStreamConsumer {
			void accept(OutputStream os) throws Exception;
		}

		@FunctionalInterface
		interface Output {
			void accept(int len, OutputStreamConsumer os) throws Exception;
		}

		void run(URI uri, Headers requestHeaders, Headers responseHeaders, NotModified match, Output output)
				throws Exception;

		default void remove(Object key) {
		}
	}

	private static class ETagCache<K> implements Cache {
		private static class DataItem {
			private final String etag;
			private final byte[] data;

			DataItem(byte[] data) {
				this.data = data;
				this.etag = "\"" + Base64.getEncoder().encodeToString(MD5.digest(data)) + "\"";
			}
		}

		private final Map<K, DataItem> cache = new ConcurrentHashMap<>();
		private final Function<URI, K> uri2key;
		private final Function<K, byte[]> loader;

		public ETagCache(Function<URI, K> uri2key, Function<K, byte[]> loader) {
			this.uri2key = uri2key;
			this.loader = loader;
		}

		public void run(URI uri, Headers requestHeaders, Headers responseHeaders, NotModified match, Output output)
				throws Exception {
			K key = uri2key.apply(uri);
			DataItem item = cache.get(key);
			if (item == null)
				cache.put(key, item = new DataItem(loader.apply(key)));
			else {
				List<String> ifNoneMatch = requestHeaders.get("If-None-Match");
				String etag = ifNoneMatch == null ? null : ifNoneMatch.get(0);
				if (etag != null && etag.equals(item.etag)) {
					match.run();
					return;
				}
			}
			responseHeaders.add("ETag", item.etag);
			byte[] data = item.data;
			output.accept(data.length, os -> os.write(data));
		}

		public void remove(Object key) {
			cache.remove(key);
		}
	}
}
