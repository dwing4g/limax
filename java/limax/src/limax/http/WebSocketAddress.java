package limax.http;

import java.net.SocketAddress;
import java.net.URI;

public final class WebSocketAddress extends SocketAddress {
	private static final long serialVersionUID = -6917933681459656595L;

	private final SocketAddress socketAddress;
	private final URI contextURI;
	private final URI requestURI;
	private final URI origin;

	public WebSocketAddress(SocketAddress socketAddress, URI contextURI, URI requestURI, URI origin) {
		this.socketAddress = socketAddress;
		this.contextURI = contextURI;
		this.requestURI = requestURI;
		this.origin = origin;
	}

	public SocketAddress getSocketAddress() {
		return socketAddress;
	}

	public URI getContextURI() {
		return contextURI;
	}

	public URI getRequestURI() {
		return requestURI;
	}

	public URI getOrigin() {
		return origin;
	}

	@Override
	public String toString() {
		return socketAddress + "," + contextURI + "," + requestURI + "," + origin;
	}
}
