package limax.endpoint;

import java.util.concurrent.Executor;

import limax.endpoint.script.ScriptEngineHandle;
import limax.net.State;

public interface EndpointConfigBuilder {
	EndpointConfigBuilder inputBufferSize(int inputBufferSize);

	EndpointConfigBuilder outputBufferSize(int outputBufferSize);

	EndpointConfigBuilder executor(Executor executor);

	EndpointConfigBuilder aunayService(boolean used);

	EndpointConfigBuilder keepAlive(boolean used);
	
	EndpointConfigBuilder endpointState(State... states);

	EndpointConfigBuilder staticViewClasses(View.StaticManager... managers);

	EndpointConfigBuilder variantProviderIds(int... pvids);

	EndpointConfigBuilder scriptEngineHandle(ScriptEngineHandle handle);

	EndpointConfig build();
}
