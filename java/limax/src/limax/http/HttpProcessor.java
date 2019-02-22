package limax.http;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;

import limax.http.HttpServer.Parameter;
import limax.net.io.NetProcessor;
import limax.net.io.NetTask;

class HttpProcessor implements NetProcessor {
	private final EnumMap<Parameter, Object> parameters;
	private final Host defaultHost;
	private final Map<String, Host> hosts;
	private NetTask nettask;
	private InetSocketAddress local;
	private InetSocketAddress peer;
	private ProtocolProcessor processor;
	private long http11RequestTimeout;
	private ByteBuffer remain;

	HttpProcessor(EnumMap<Parameter, Object> parameters, Host defaultHost, Map<String, Host> hosts) {
		this.parameters = parameters;
		this.defaultHost = defaultHost;
		this.hosts = hosts;
	}

	void upgrade(ProtocolProcessor processor) {
		this.processor = processor;
	}

	void pipeline() {
		(processor = new Http11Exchange(this, nettask, http11RequestTimeout)).process(remain);
		if (!remain.hasRemaining()) {
			nettask.resetAlarm(http11RequestTimeout);
			nettask.enable();
		}
	}

	Handler getHandler(String dnsName, String path) {
		Host host = (dnsName != null ? hosts.getOrDefault(Host.normalizeDnsName(dnsName), defaultHost) : defaultHost);
		Handler handler = host.find(path);
		return handler != null ? handler
				: path.equals("*") ? (HttpHandler) parameters.get(Parameter.HANDLER_ASTERISK)
						: (HttpHandler) parameters.get(Parameter.HANDLER_404);
	}

	InetSocketAddress getLocalAddress() {
		return local;
	}

	InetSocketAddress getPeerAddress() {
		return peer;
	}

	Object get(Parameter name) {
		return parameters.get(name);
	}

	@Override
	public void process(byte[] in) throws Exception {
		long timeout = processor.process(remain = ByteBuffer.wrap(in));
		if (timeout >= 0)
			nettask.resetAlarm(timeout);
		else if (timeout == ProtocolProcessor.DISABLE_INCOMING) {
			nettask.resetAlarm(0);
			nettask.disable();
		}
	}

	@Override
	public void shutdown(Throwable closeReason) {
		processor.shutdown(closeReason);
	}

	@Override
	public boolean startup(NetTask nettask, SocketAddress local, SocketAddress peer) throws Exception {
		this.nettask = nettask;
		this.local = (InetSocketAddress) local;
		this.peer = (InetSocketAddress) peer;
		this.http11RequestTimeout = (Long) get(Parameter.HTTP11_REQUEST_TIMEOUT);
		this.processor = new Http11Exchange(this, nettask, http11RequestTimeout);
		nettask.resetAlarm(http11RequestTimeout);
		return true;
	}
}
