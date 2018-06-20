package limax.endpoint;

import limax.codec.Octets;

public interface TunnelSupport {
	void onTunnel(int providerid, int label, Octets data) throws Exception;

	void registerTunnelSender(TunnelSender sender);
}
