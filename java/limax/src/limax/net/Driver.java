package limax.net;

public interface Driver {
	Class<? extends Config> getConfigClass();

	Class<? extends Listener> getListenerClass();

	Manager newInstance(Config config, Listener listener, Manager wrapper) throws Exception;

}
