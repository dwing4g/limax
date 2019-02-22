package limax.http;

import java.net.InetSocketAddress;
import java.net.URI;

public interface HttpExchange {
	InetSocketAddress getLocalAddress();

	InetSocketAddress getPeerAddress();

	boolean isRequestFinished();

	URI getRequestURI();

	String getRequestMethod();

	Headers getRequestHeaders();

	Headers getRequestTrailers();

	FormData getFormData();

	Headers getResponseHeaders();

	Headers getResponseTrailers();

}
