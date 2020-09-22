package limax.xmlconfig;

import java.net.SocketAddress;

import limax.net.ClientManagerConfig;
import limax.net.State;
import limax.util.Dispatcher;

public class ClientManagerConfigBuilder implements ConfigBuilder {
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
	private SocketAddress peerAddress = null;
	private boolean autoReconnect = false;
	private long connectTimeout = 5000;
	private boolean asynchronous;

	public ClientManagerConfigBuilder() {
	}

	public ClientManagerConfigBuilder(ClientManagerConfig config) {
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
		this.peerAddress = config.getPeerAddress();
		this.autoReconnect = config.isAutoReconnect();
		this.connectTimeout = config.getConnectTimeout();
		this.asynchronous = config.isAsynchronous();
	}

	@Override
	public ClientManagerConfig build() {
		return new ClientManagerConfig() {
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
			public SocketAddress getPeerAddress() {
				return peerAddress;
			}

			@Override
			public boolean isAutoReconnect() {
				return autoReconnect;
			}

			@Override
			public long getConnectTimeout() {
				return connectTimeout;
			}

			@Override
			public String toString() {
				return name + " " + peerAddress;
			}

			@Override
			public boolean isAsynchronous() {
				return asynchronous;
			}
		};
	}

	public ClientManagerConfigBuilder name(String name) {
		this.name = name;
		return this;
	}

	public ClientManagerConfigBuilder inputBufferSize(int inputBufferSize) {
		this.inputBufferSize = inputBufferSize;
		return this;
	}

	public ClientManagerConfigBuilder outputBufferSize(int outputBufferSize) {
		this.outputBufferSize = outputBufferSize;
		return this;
	}

	public ClientManagerConfigBuilder checkOutputBuffer(boolean checkOutputBuffer) {
		this.checkOutputBuffer = checkOutputBuffer;
		return this;
	}

	public ClientManagerConfigBuilder outputSecurity(byte[] outputSecurity) {
		this.outputSecurity = outputSecurity;
		return this;
	}

	public ClientManagerConfigBuilder inputSecurity(byte[] inputSecurity) {
		this.inputSecurity = inputSecurity;
		return this;
	}

	public ClientManagerConfigBuilder outputCompress(boolean outputCompress) {
		this.outputCompress = outputCompress;
		return this;
	}

	public ClientManagerConfigBuilder inputCompress(boolean inputCompress) {
		this.inputCompress = inputCompress;
		return this;
	}

	public ClientManagerConfigBuilder defaultState(State defaultState) {
		this.defaultState = defaultState;
		return this;
	}

	public ClientManagerConfigBuilder dispatcher(Dispatcher dispatcher) {
		this.dispatcher = dispatcher;
		return this;
	}

	public ClientManagerConfigBuilder peerAddress(SocketAddress peerAddress) {
		this.peerAddress = peerAddress;
		return this;
	}

	public ClientManagerConfigBuilder autoReconnect(boolean autoReconnect) {
		this.autoReconnect = autoReconnect;
		return this;
	}

	public ClientManagerConfigBuilder connectTimeout(long connectTimeout) {
		this.connectTimeout = connectTimeout;
		return this;
	}

	public ClientManagerConfigBuilder asynchronous(boolean asynchronous) {
		this.asynchronous = asynchronous;
		return this;
	}
}
