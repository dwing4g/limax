package limax.xmlconfig;

import java.net.SocketAddress;

import javax.net.ssl.SSLContext;

import limax.net.ServerManagerConfig;
import limax.net.State;
import limax.util.Dispatcher;
import limax.util.Limit;

public class ServerManagerConfigBuilder implements ConfigBuilder {
	private String name;
	private int inputBufferSize = 1024 * 8;
	private int outputBufferSize = 1024 * 8;
	private boolean checkOutputBuffer = false;
	private byte[] outputSecurity = null;
	private byte[] inputSecurity = null;
	private boolean outputCompress = false;
	private boolean inputCompress = false;
	private State defaultState;
	private Dispatcher dispatcher;
	private SocketAddress localAddress;
	private int backlog = 32;
	private Limit limit = Limit.get("");
	private boolean autoListen = true;
	private boolean webSocketEnabled = false;
	private SSLContext sslContext;
	private boolean asynchronous;

	public ServerManagerConfigBuilder() {
	}

	public ServerManagerConfigBuilder(ServerManagerConfig config) {
		this.name = config.getName();
		this.inputBufferSize = config.getInputBufferSize();
		this.outputBufferSize = config.getOutputBufferSize();
		this.checkOutputBuffer = config.isCheckOutputBuffer();
		this.outputSecurity = config.getOutputSecurityBytes();
		this.inputSecurity = config.getInputSecurityBytes();
		this.outputCompress = config.isOutputCompress();
		this.inputCompress = config.isInputCompress();
		this.defaultState = config.getDefaultState();
		this.dispatcher = config.getDispatcher();
		this.localAddress = config.getLocalAddress();
		this.backlog = config.getBacklog();
		this.limit = config.getTransportLimit();
		this.autoListen = config.isAutoListen();
		this.webSocketEnabled = config.isWebSocketEnabled();
		this.sslContext = config.getSSLContext();
		this.asynchronous = config.isAsynchronous();
	}

	@Override
	public ServerManagerConfig build() {
		return new ServerManagerConfig() {
			@Override
			public String getName() {
				return name;
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
				return checkOutputBuffer;
			}

			@Override
			public byte[] getOutputSecurityBytes() {
				return outputSecurity;
			}

			@Override
			public byte[] getInputSecurityBytes() {
				return inputSecurity;
			}

			@Override
			public boolean isOutputCompress() {
				return outputCompress;
			}

			@Override
			public boolean isInputCompress() {
				return inputCompress;
			}

			@Override
			public State getDefaultState() {
				return defaultState;
			}

			@Override
			public Dispatcher getDispatcher() {
				return dispatcher;
			}

			@Override
			public SocketAddress getLocalAddress() {
				return localAddress;
			}

			@Override
			public int getBacklog() {
				return backlog;
			}

			@Override
			public Limit getTransportLimit() {
				return limit;
			}

			@Override
			public boolean isAutoListen() {
				return autoListen;
			}

			@Override
			public boolean isWebSocketEnabled() {
				return webSocketEnabled;
			}

			@Override
			public SSLContext getSSLContext() {
				return sslContext;
			}

			@Override
			public String toString() {
				return name + " " + localAddress;
			}

			@Override
			public boolean isAsynchronous() {
				return asynchronous;
			}
		};
	}

	public ServerManagerConfigBuilder name(String name) {
		this.name = name;
		return this;
	}

	public ServerManagerConfigBuilder inputBufferSize(int inputBufferSize) {
		this.inputBufferSize = inputBufferSize;
		return this;
	}

	public ServerManagerConfigBuilder outputBufferSize(int outputBufferSize) {
		this.outputBufferSize = outputBufferSize;
		return this;
	}

	public ServerManagerConfigBuilder checkOutputBuffer(boolean checkOutputBuffer) {
		this.checkOutputBuffer = checkOutputBuffer;
		return this;
	}

	public ServerManagerConfigBuilder outputSecurity(byte[] outputSecurity) {
		this.outputSecurity = outputSecurity;
		return this;
	}

	public ServerManagerConfigBuilder inputSecurity(byte[] inputSecurity) {
		this.inputSecurity = inputSecurity;
		return this;
	}

	public ServerManagerConfigBuilder outputCompress(boolean outputCompress) {
		this.outputCompress = outputCompress;
		return this;
	}

	public ServerManagerConfigBuilder inputCompress(boolean inputCompress) {
		this.inputCompress = inputCompress;
		return this;
	}

	public ServerManagerConfigBuilder defaultState(State defaultState) {
		this.defaultState = defaultState;
		return this;
	}

	public ServerManagerConfigBuilder dispatcher(Dispatcher dispatcher) {
		this.dispatcher = dispatcher;
		return this;
	}

	public ServerManagerConfigBuilder localAddress(SocketAddress localAddress) {
		this.localAddress = localAddress;
		return this;
	}

	public ServerManagerConfigBuilder backlog(int backlog) {
		this.backlog = backlog;
		return this;
	}

	public ServerManagerConfigBuilder limit(Limit limit) {
		this.limit = limit;
		return this;
	}

	public ServerManagerConfigBuilder autoListen(boolean autoListen) {
		this.autoListen = autoListen;
		return this;
	}

	public ServerManagerConfigBuilder webSocketEnabled(boolean webSocketEnabled) {
		this.webSocketEnabled = webSocketEnabled;
		return this;
	}

	public ServerManagerConfigBuilder sslContext(SSLContext sslContext) {
		this.sslContext = sslContext;
		return this;
	}

	public ServerManagerConfigBuilder asynchronous(boolean asynchronous) {
		this.asynchronous = asynchronous;
		return this;
	}
}
