package limax.net;

import java.net.SocketAddress;
import java.net.URI;

public final class WebSocketAddress extends SocketAddress {
	private static final long serialVersionUID = -6917933681459656595L;

	private final SocketAddress socketAddress;
	private final URI requestURI;
	private final URI origin;

	public WebSocketAddress(SocketAddress socketAddress, URI requestURI, URI origin) {
		this.socketAddress = socketAddress;
		this.requestURI = requestURI;
		this.origin = origin;
	}

	public SocketAddress getSocketAddress() {
		return socketAddress;
	}

	public URI getRequestURI() {
		return requestURI;
	}

	public URI getOrigin() {
		return origin;
	}

	@Override
	public String toString() {
		return socketAddress + "/" + requestURI + "/" + origin;
	}
}
