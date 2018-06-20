package limax.net;

import limax.codec.CodecException;
import limax.codec.Octets;

/**
 * transport capability
 */
public interface SupportTypedDataTransfer {
	void send(int type, Octets data) throws CodecException;
}
