package limax.auany.json;

import limax.codec.JSONSerializable;

public final class SwitcherInfo implements JSONSerializable {
	private final String host;
	private final int port;

	public SwitcherInfo(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SwitcherInfo))
			return false;
		SwitcherInfo info = (SwitcherInfo) o;
		return info.host.equals(this.host) && info.port == this.port;
	}
}
