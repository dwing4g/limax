package limax.endpoint;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Executor;

import limax.endpoint.script.ScriptEngineHandle;
import limax.net.ClientManagerConfig;
import limax.net.Engine;
import limax.net.State;
import limax.util.Dispatcher;

final class EndpointConfigBuilderImpl implements EndpointConfigBuilder {
	private final String serverIp;
	private final int serverPort;
	private final LoginConfig loginConfig;
	private final boolean pingServerOnly;

	private volatile int outputBufferSize = 8 * 1024;
	private volatile int inputBufferSize = 8 * 1024;
	private volatile Executor executor;
	private volatile State endpointState = new State();
	private volatile boolean auanyServiceUsed = true;
	private volatile boolean keepAliveUsed = true;

	private final Map<Integer, Map<Short, Class<? extends View>>> svclasses = new HashMap<Integer, Map<Short, Class<? extends View>>>();
	private Collection<Integer> variantpvids = new HashSet<Integer>();
	private ScriptEngineHandle scriptEngineHandle;

	EndpointConfigBuilderImpl(String serverIp, int serverPort, LoginConfig loginConfig, boolean pingServerOnly) {
		this.serverIp = serverIp;
		this.serverPort = serverPort;
		this.loginConfig = loginConfig;
		this.pingServerOnly = pingServerOnly;
		this.executor = Engine.getApplicationExecutor().getExecutor(this);
		this.endpointState.merge(limax.endpoint.states.Endpoint.EndpointClient);
	}

	@Override
	public EndpointConfigBuilder inputBufferSize(int inputBufferSize) {
		this.inputBufferSize = inputBufferSize;
		return this;
	}

	@Override
	public EndpointConfigBuilder outputBufferSize(int outputBufferSize) {
		this.outputBufferSize = outputBufferSize;
		return this;
	}

	@Override
	public EndpointConfigBuilder endpointState(State... states) {
		endpointState = new State();
		endpointState.merge(limax.endpoint.states.Endpoint.EndpointClient);
		for (State state : states)
			endpointState.merge(state);
		return this;
	}

	@Override
	public EndpointConfigBuilder variantProviderIds(int... pvids) {
		variantpvids.clear();
		for (int id : pvids)
			variantpvids.add(id);
		return this;
	}

	@Override
	public EndpointConfigBuilder staticViewClasses(View.StaticManager... managers) {
		svclasses.clear();
		for (View.StaticManager m : managers)
			svclasses.put(m.getProviderId(), m.getClasses());
		return this;
	}

	@Override
	public EndpointConfigBuilder scriptEngineHandle(ScriptEngineHandle handle) {
		scriptEngineHandle = handle;
		return this;
	}

	@Override
	public EndpointConfigBuilder executor(Executor executor) {
		this.executor = executor;
		return this;
	}

	@Override
	public EndpointConfigBuilder aunayService(boolean used) {
		this.auanyServiceUsed = used;
		return this;
	}

	@Override
	public EndpointConfigBuilder keepAlive(boolean used) {
		keepAliveUsed = used;
		return this;
	}

	private final ClientManagerConfig clientConfig = new ClientManagerConfig() {
		@Override
		public String getName() {
			return "Endpoint";
		}

		@Override
		public int getInputBufferSize() {
			return inputBufferSize;
		}

		@Override
		public int getOutputBufferSize() {
			return outputBufferSize;
		}

		@Override
		public boolean isCheckOutputBuffer() {
			return true;
		}

		@Override
		public byte[] getOutputSecurityBytes() {
			return null;
		}

		@Override
		public byte[] getInputSecurityBytes() {
			return null;
		}

		@Override
		public boolean isOutputCompress() {
			return false;
		}

		@Override
		public boolean isInputCompress() {
			return false;
		}

		@Override
		public State getDefaultState() {
			return limax.endpoint.states.Endpoint.getDefaultState();
		}

		@Override
		public Dispatcher getDispatcher() {
			return new Dispatcher(executor);
		}

		@Override
		public SocketAddress getPeerAddress() {
			return new InetSocketAddress(serverIp, serverPort);
		}

		@Override
		public boolean isAutoReconnect() {
			return false;
		}

		@Override
		public long getConnectTimeout() {
			return 5000;
		}

		@Override
		public boolean isAsynchronous() {
			return false;
		}

	};

	@Override
	public EndpointConfig build() {
		return new EndpointConfig() {

			@Override
			public int getDHGroup() {
				return 2;
			}

			@Override
			public ClientManagerConfig getClientManagerConfig() {
				return clientConfig;
			}

			@Override
			public LoginConfig getLoginConfig() {
				return loginConfig;
			}

			@Override
			public boolean isPingServerOnly() {
				return pingServerOnly;
			}

			@Override
			public boolean auanyService() {
				return auanyServiceUsed;
			}

			@Override
			public boolean keepAlive() {
				return keepAliveUsed;
			}

			@Override
			public State getEndpointState() {
				return endpointState;
			}

			@Override
			public Map<Integer, Map<Short, Class<? extends View>>> getStaticViewClasses() {
				return Collections.unmodifiableMap(svclasses);
			}

			@Override
			public Collection<Integer> getVariantProviderIds() {
				return Collections.unmodifiableCollection(variantpvids);
			}

			@Override
			public ScriptEngineHandle getScriptEngineHandle() {
				return scriptEngineHandle;
			}
		};
	}
}
