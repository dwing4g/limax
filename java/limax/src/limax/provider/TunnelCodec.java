package limax.provider;

import limax.codec.Octets;

public interface TunnelCodec {
	Octets transform(int label, Octets data) throws TunnelException;
}
