package limax.endpoint.script;

public interface TunnelReceiver {
	void onTunnel(int providerid, int label, String data);
}
