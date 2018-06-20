package limax.provider;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import limax.codec.CodecException;
import limax.net.ClientListener;
import limax.net.ClientManager;
import limax.net.ClientManagerConfig;
import limax.net.Config;
import limax.net.Engine;
import limax.net.Manager;
import limax.net.ManagerConfig;
import limax.net.Protocol;
import limax.net.ServerListener;
import limax.net.SizePolicyException;
import limax.net.Transport;
import limax.provider.switcherprovider.Kick;
import limax.util.Trace;

class ProviderSwitcherExchanger implements ServerListener, ClientListener {
	private final ProviderManagerImpl provider;
	private final Set<Transport> transports = Collections.newSetFromMap(new ConcurrentHashMap<Transport, Boolean>());
	private boolean closed = false;

	class SessionObject {
		private Set<Long> sessionids = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());

		void add(long sessionid) {
			sessionids.add(sessionid);
		}

		void remove(long sessionid) {
			sessionids.remove(sessionid);
		}

		boolean contains(long sessionid) {
			return sessionids.contains(sessionid);
		}

		void forEach(Consumer<? super Long> action) {
			sessionids.forEach(action);
		}

		ProviderManagerImpl getProviderManager() {
			return provider;
		}

		@Override
		public String toString() {
			return "sessioncount=" + sessionids.size();
		}
	}

	ProviderSwitcherExchanger(ProviderManagerImpl provider) {
		this.provider = provider;
	}

	Manager add(ManagerConfig config) throws Exception {
		try {
			return Engine.add(config, this, provider);
		} catch (Exception e) {
			if (Trace.isErrorEnabled())
				Trace.error("ProviderSwitcherListener.add " + config, e);
			throw e;
		}
	}

	@Override
	public void onManagerInitialized(Manager manager, Config config) {
	}

	@Override
	public void onManagerUninitialized(Manager manager) {
	}

	@Override
	public void onTransportAdded(Transport transport) throws Exception {
		try {
			provider.tryBind(transport);
		} catch (Exception e) {
			Manager manager = transport.getManager();
			if (Trace.isErrorEnabled())
				Trace.error(manager.getConfig() + " trybind exception", e);
			if (manager instanceof ClientManager)
				add((ClientManagerConfig) manager.getConfig());
			throw e;
		}
		transport.setSessionObject(new SessionObject());
		transports.add(transport);
		if (provider.isStateless())
			provider.getListener().onTransportAdded(transport);
		else if (Trace.isInfoEnabled())
			Trace.info(provider.getListener() + " onTransportAdded " + transport);
	}

	@Override
	public void onTransportRemoved(Transport transport) throws Exception {
		transports.remove(transport);
		SessionObject so = ((SessionObject) transport.getSessionObject());
		if (provider.isStateless()) {
			so.forEach(sessionid -> provider.statelessRemoveTransport(sessionid));
			provider.getListener().onTransportRemoved(transport);
		} else {
			so.forEach(sessionid -> provider.removeTransport1(sessionid));
			if (Trace.isInfoEnabled())
				Trace.info(provider.getListener() + " onTransportRemoved " + transport, transport.getCloseReason());
		}
	}

	@Override
	public void onAbort(Transport transport) throws Exception {
		if (Trace.isWarnEnabled())
			Trace.warn(provider.getListener() + " onAbort " + transport.getManager().getConfig() + " "
					+ transport.getCloseReason());
	}

	synchronized void close() {
		closed = true;
	}

	synchronized boolean addSession(Transport transport, long sessionid) {
		if (closed)
			return false;
		((SessionObject) transport.getSessionObject()).add(sessionid);
		return true;
	}

	void removeSession(Transport transport, long sessionid) {
		((SessionObject) transport.getSessionObject()).remove(sessionid);
	}

	private void send(Protocol protocol, Transport transport)
			throws InstantiationException, ClassCastException, SizePolicyException, CodecException {
		try {
			protocol.send(transport);
		} catch (Exception e) {
			if (Trace.isWarnEnabled())
				Trace.warn("send " + protocol + " on " + transport, e);
			throw e;
		}
	}

	void send(Protocol protocol) throws CodecException {
		CodecException exception = null;
		for (Transport transport : transports)
			try {
				send(protocol, transport);
			} catch (Exception e) {
				if (exception == null)
					exception = new CodecException();
				exception.addSuppressed(e);
			}
		if (exception != null)
			throw exception;
	}

	void sendAny(Protocol protocol) {
		for (Transport transport : transports)
			try {
				send(protocol, transport);
				return;
			} catch (Exception e) {
			}
	}

	void send(Protocol protocol, long sessionid)
			throws InstantiationException, ClassCastException, SizePolicyException, CodecException {
		send(protocol,
				transports.stream().filter(t -> ((SessionObject) t.getSessionObject()).contains(sessionid)).findAny()
						.orElseThrow(
								() -> new CodecException("send " + protocol + " on " + sessionid + " miss transport")));
	}

	void send(Protocol protocol, Collection<Long> ids) throws CodecException {
		CodecException exception = null;
		for (Transport transport : transports) {
			SessionObject so = (SessionObject) transport.getSessionObject();
			if (ids.stream().filter(id -> so.contains(id)).findAny().isPresent())
				try {
					send(protocol, transport);
				} catch (Exception e) {
					if (exception == null)
						exception = new CodecException();
					exception.addSuppressed(e);
				}
		}
		if (exception != null)
			throw exception;
	}

	void kick(long sessionid, int errorcode) {
		try {
			send(new Kick(sessionid, errorcode), sessionid);
		} catch (Exception e) {
		}
	}
}
