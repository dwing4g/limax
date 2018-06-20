package limax.net;

import limax.codec.CodecException;
import limax.codec.Octets;

/**
 * manager capability
 */
public interface SupportTypedDataBroadcast {
	void broadcast(int type, Octets data)
			throws InstantiationException, SizePolicyException, CodecException, ClassCastException;
}
