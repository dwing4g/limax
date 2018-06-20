package limax.endpoint;

import limax.codec.CodecException;
import limax.codec.Octets;
import limax.net.SizePolicyException;

public interface TunnelSender {
	void send(int providerid, int label, Octets data)
			throws InstantiationException, SizePolicyException, CodecException, ClassCastException;
}
