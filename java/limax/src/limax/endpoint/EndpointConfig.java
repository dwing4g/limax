package limax.endpoint;

import java.util.Collection;
import java.util.Map;

import limax.endpoint.script.ScriptEngineHandle;
import limax.net.ClientManagerConfig;
import limax.net.Config;
import limax.net.State;

public interface EndpointConfig extends Config {

	int getDHGroup();

	ClientManagerConfig getClientManagerConfig();

	LoginConfig getLoginConfig();

	boolean isPingServerOnly();

	boolean auanyService();

	boolean keepAlive();
	
	State getEndpointState();

	Map<Integer, Map<Short, Class<? extends View>>> getStaticViewClasses();

	Collection<Integer> getVariantProviderIds();

	ScriptEngineHandle getScriptEngineHandle();
}
