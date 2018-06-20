package limax.net;

public interface ClientListener extends Listener {
	void onAbort(Transport transport) throws Exception;
}
