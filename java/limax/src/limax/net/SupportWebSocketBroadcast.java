package limax.net;

import limax.codec.CodecException;

public interface SupportWebSocketBroadcast {
	void broadcast(String data) throws CodecException, ClassCastException;

	void broadcast(byte[] data) throws CodecException, ClassCastException;
}
