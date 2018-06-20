package limax.endpoint;

import limax.codec.CodecException;
import limax.net.SizePolicyException;

public interface ViewContext {
	enum Type {
		Static, Variant, Script
	}

	View getSessionOrGlobalView(short classindex);

	TemporaryView findTemporaryView(short classindex, int instanceindex);

	EndpointManager getEndpointManager();

	int getProviderId();

	Type getType();

	void sendMessage(View view, String msg) throws InstantiationException, SizePolicyException, CodecException;

}
