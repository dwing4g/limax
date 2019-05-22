package limax.http;

import java.net.InetSocketAddress;
import java.net.URI;

import javax.net.ssl.SSLSession;

public interface HttpExchange {
	InetSocketAddress getLocalAddress();

	InetSocketAddress getPeerAddress();

	Host getHost();

	URI getContextURI();

	URI getRequestURI();

	String getRequestMethod();

	Headers getRequestHeaders();

	Headers getRequestTrailers();

	FormData getFormData();

	Headers getResponseHeaders();

	Headers getResponseTrailers();

	SSLSession getSSLSession();

	void async(HttpHandler handler);

	void cancel(Throwable closeReason);

	default void promise(URI uri) {
	}
}
