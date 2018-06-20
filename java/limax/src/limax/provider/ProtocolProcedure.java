package limax.provider;

import limax.net.Protocol;
import limax.zdb.Procedure;

public abstract class ProtocolProcedure<T extends Protocol> implements Procedure {
	private final T protocol;

	protected ProtocolProcedure(T p) {
		this.protocol = p;
	}

	public final T getProtocol() {
		return protocol;
	}

	protected final long tryGetSessionId() {
		return ((ProviderTransport) protocol.getTransport()).getSessionId();
	}

}
