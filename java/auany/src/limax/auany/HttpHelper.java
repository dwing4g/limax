package limax.auany;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import limax.auany.appconfig.AppKey;
import limax.auany.appconfig.ServiceType;
import limax.codec.CodecException;
import limax.codec.JSON;
import limax.codec.JSONException;
import limax.codec.JSONSerializable;
import limax.codec.SHA1;
import limax.http.DataSupplier;
import limax.http.Headers;
import limax.http.HttpException;
import limax.http.HttpHandler;

public final class HttpHelper {
	private HttpHelper() {
	}

	public static HttpHandler createHttpHandler(Cache cache) {
		return exchange -> cache.run(exchange.getRequestURI(), exchange.getRequestHeaders(),
				exchange.getResponseHeaders());
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
		return (uri, requestHeaders, responseHeaders) -> {
			responseHeaders.set("Cache-control", "no-cache");
			responseHeaders.set("Access-Control-Allow-Origin", "*");
			try {
				return DataSupplier.from(JSON.stringify(loader.apply(uri)), StandardCharsets.UTF_8);
			} catch (JSONException e) {
				throw new HttpException(HttpURLConnection.HTTP_BAD_REQUEST, true);
			}
		};
	}

	public static <K> Cache makeJSONCache(Function<URI, K> uri2key, Function<K, JSONSerializable> jsonloader) {
		return new ETagCache<K>(uri2key, jsonloader.andThen(encodeJSON(StandardCharsets.UTF_8)));
	}

	public interface Cache {
		DataSupplier run(URI uri, Headers requestHeaders, Headers responseHeaders);

		default void remove(Object key) {
		}
	}

	private static class ETagCache<K> implements Cache {
		private static class DataItem {
			private final String etag;
			private final byte[] data;

			DataItem(byte[] data) {
				this.data = data;
				this.etag = Base64.getUrlEncoder().encodeToString(SHA1.digest(data));
			}
		}

		private final Map<K, DataItem> cache = new ConcurrentHashMap<>();
		private final Function<URI, K> uri2key;
		private final Function<K, byte[]> loader;

		public ETagCache(Function<URI, K> uri2key, Function<K, byte[]> loader) {
			this.uri2key = uri2key;
			this.loader = loader;
		}

		public DataSupplier run(URI uri, Headers requestHeaders, Headers responseHeaders) {
			K key = uri2key.apply(uri);
			DataItem item = cache.get(key);
			responseHeaders.set("Access-Control-Allow-Origin", "*");
			if (item == null)
				cache.put(key, item = new DataItem(loader.apply(key)));
			else if (item.etag.equals(requestHeaders.getFirst("If-None-Match"))) {
				responseHeaders.set(":status", HttpURLConnection.HTTP_NOT_MODIFIED);
				return null;
			}
			responseHeaders.set("ETag", item.etag);
			responseHeaders.set("Connection", "close");
			return DataSupplier.from(item.data);
		}

		public void remove(Object key) {
			cache.remove(key);
		}
	}
}
