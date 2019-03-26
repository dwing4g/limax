package limax.pkix.tool;

import java.net.HttpURLConnection;
import java.util.Base64;

import limax.codec.SHA1;
import limax.http.DataSupplier;
import limax.http.Headers;
import limax.http.HttpExchange;
import limax.http.HttpHandler;

class StaticWebData implements HttpHandler {
	private final byte[] content;
	private final String etag;
	private final String contentType;

	StaticWebData(byte[] content, String contentType) {
		this.content = content;
		this.contentType = contentType;
		this.etag = Base64.getUrlEncoder().encodeToString(SHA1.digest(this.content));
	}

	@Override
	public DataSupplier handle(HttpExchange exchange) throws Exception {
		Headers reqheaders = exchange.getRequestHeaders();
		Headers repheaders = exchange.getResponseHeaders();
		if (etag.equals(reqheaders.getFirst("If-None-Match"))) {
			repheaders.set(":status", HttpURLConnection.HTTP_NOT_MODIFIED);
			return null;
		}
		repheaders.set("Content-Type", contentType);
		repheaders.set("ETag", etag);
		repheaders.set("Connection", "close");
		return DataSupplier.from(content);
	}
}
