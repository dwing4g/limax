package limax.provider;

import limax.codec.Octets;

public interface TunnelSupport {
	void onTunnel(long sessionid, int label, Octets data) throws Exception;

	default void onException(long sessionid, int label, TunnelException exception) throws Exception {
		throw exception;
	}
}
