package limax.p2p;

import java.net.InetSocketAddress;
import java.util.Collection;

import limax.util.Pair;

@FunctionalInterface
public interface NetworkSearch {
	Pair<Boolean, Collection<NetworkID>> apply(DHTAddress searchFor, InetSocketAddress inetSocketAddress)
			throws Exception;
}
