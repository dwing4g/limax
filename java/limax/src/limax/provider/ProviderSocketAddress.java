package limax.provider;

import java.net.SocketAddress;

public final class ProviderSocketAddress extends SocketAddress {
	private static final long serialVersionUID = 8611836282296151760L;

	private final long sessionid;

	public ProviderSocketAddress(long sessionid) {
		this.sessionid = sessionid;
	}

	public long getSessionId() {
		return sessionid;
	}

	@Override
	public String toString() {
		return Long.toString(sessionid);
	}

}
