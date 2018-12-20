package limax.pkix.tool;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Base64;
import java.util.List;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import limax.codec.SHA1;

class StaticWebData {
	private final byte[] content;
	private final String etag;
	private final String contentType;

	StaticWebData(byte[] content, String contentType) {
		this.content = content;
		this.contentType = contentType;
		this.etag = Base64.getEncoder().encodeToString(SHA1.digest(this.content));
	}

	void transfer(HttpExchange exchange) throws IOException {
		Headers headers = exchange.getRequestHeaders();
		List<String> etags = headers.get("If-None-Match");
		if (etags != null)
			for (String e : etags)
				if (etag.equals(e)) {
					exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_MODIFIED, -1);
					return;
				}
		headers = exchange.getResponseHeaders();
		headers.set("Content-Type", contentType);
		headers.set("ETag", etag);
		headers.set("Connection", "close");
		exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, content.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(content);
		}
	}
}
