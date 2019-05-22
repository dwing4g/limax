package limax.http;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

import limax.http.HttpServer.Parameter;
import limax.net.Engine;
import limax.net.io.NetProcessor;
import limax.net.io.NetTask;

class HttpProcessor implements NetProcessor {
	private final static Method alpnMethod;
	private final static BiFunction<SSLEngine, List<String>, String> h2Selector;
	private final EnumMap<Parameter, Object> parameters;
	private final Host defaultHost;
	private final Map<String, Host> hosts;
	private NetTask nettask;
	private InetSocketAddress local;
	private InetSocketAddress peer;
	private ProtocolProcessor processor;
	private boolean sniHostNameUnchecked;
	private String sniHostName;

	static {
		Method method = null;
		try {
			method = SSLEngine.class.getMethod("setHandshakeApplicationProtocolSelector", BiFunction.class);
		} catch (Exception e) {
		}
		alpnMethod = method;
		h2Selector = (engine, protocols) -> {
			for (String protocol : protocols)
				if (protocol.equals("h2"))
					return protocol;
			return "";
		};
	}

	HttpProcessor(EnumMap<Parameter, Object> parameters, Host defaultHost, Map<String, Host> hosts) {
		this.parameters = parameters;
		this.defaultHost = defaultHost;
		this.hosts = hosts;
	}

	void replace(ProtocolProcessor processor) {
		this.processor = processor;
	}

	Host find(String dnsName) {
		if (sniHostName != null)
			dnsName = sniHostName;
		return dnsName != null ? hosts.getOrDefault(Host.normalizeDnsName(dnsName), defaultHost) : defaultHost;
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

	void execute(Runnable r) {
		Engine.getApplicationExecutor().execute(this, r);
	}

	void setSendBufferNotice(Runnable r) {
		AtomicBoolean active = new AtomicBoolean();
		nettask.setSendBufferNotice(r != null ? (remaining, att) -> {
			if (active.compareAndSet(false, true))
				execute(() -> {
					active.set(false);
					r.run();
				});
		} : null, null);
	}

	NetTask nettask() {
		return nettask;
	}

	@Override
	public void process(byte[] in) throws Exception {
		if (sniHostNameUnchecked) {
			SSLSession session = nettask.getSSLSession();
			if (session instanceof ExtendedSSLSession)
				for (SNIServerName name : ((ExtendedSSLSession) session).getRequestedServerNames())
					if (name instanceof SNIHostName) {
						sniHostName = ((SNIHostName) name).getAsciiName();
						break;
					}
			sniHostNameUnchecked = false;
		}
		processor.process(ByteBuffer.wrap(in));
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
		if (this.sniHostNameUnchecked = nettask.isSSLSupported())
			nettask.attachSSL(alpnMethod != null ? sslEngine -> {
				try {
					alpnMethod.invoke(sslEngine, h2Selector);
				} catch (Exception e) {
				}
				return sslEngine;
			} : null, null);
		long requestTimeout = (Long) get(Parameter.HTTP11_REQUEST_TIMEOUT);
		this.processor = new Http11Exchange(this, requestTimeout);
		nettask.resetAlarm(requestTimeout);
		return true;
	}
}
