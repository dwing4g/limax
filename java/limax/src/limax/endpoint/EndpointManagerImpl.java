package limax.endpoint;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import limax.codec.CodecException;
import limax.codec.HmacMD5;
import limax.codec.Octets;
import limax.defines.ErrorCodes;
import limax.defines.ErrorSource;
import limax.defines.ProviderLoginData;
import limax.defines.VariantDefines;
import limax.endpoint.ViewContext.Type;
import limax.endpoint.providerendpoint.SyncViewToClients;
import limax.endpoint.providerendpoint.Tunnel;
import limax.endpoint.script.ScriptEngineHandle;
import limax.endpoint.switcherendpoint.CHandShake;
import limax.endpoint.switcherendpoint.OnlineAnnounce;
import limax.endpoint.switcherendpoint.PingAndKeepAlive;
import limax.endpoint.switcherendpoint.ProviderLogin;
import limax.endpoint.switcherendpoint.SHandShake;
import limax.endpoint.switcherendpoint.SessionKick;
import limax.endpoint.switcherendpoint.SessionLoginByToken;
import limax.net.ClientListener;
import limax.net.ClientManager;
import limax.net.Config;
import limax.net.Engine;
import limax.net.Listener;
import limax.net.Manager;
import limax.net.SizePolicyException;
import limax.net.StateTransport;
import limax.net.SupportDispatch;
import limax.net.Transport;
import limax.util.Helper;
import limax.util.Trace;

final class EndpointManagerImpl implements EndpointManager, ClientListener, SupportDispatch {
	private static class ViewContextMap {
		private final Map<Integer, Collection<AbstractViewContext>> map = new HashMap<Integer, Collection<AbstractViewContext>>();

		void put(int pvid, AbstractViewContext vc) {
			Collection<AbstractViewContext> c = map.get(pvid);
			if (null == c)
				map.put(pvid, c = new ArrayList<AbstractViewContext>());
			c.add(vc);
		}

		void onSyncViewToClients(SyncViewToClients protocol) throws Exception {
			Collection<AbstractViewContext> c = map.get(protocol.providerid);
			if (c != null)
				for (AbstractViewContext vc : c)
					vc.onSyncViewToClients(protocol);
		}

		void clear() {
			for (Collection<AbstractViewContext> c : map.values())
				for (AbstractViewContext vc : c)
					try {
						vc.clear();
					} catch (Exception e) {
					}
			map.clear();
		}

		ViewContext getViewContext(int pvid, Type type) {
			Collection<AbstractViewContext> c = map.get(pvid);
			if (c != null)
				for (AbstractViewContext vc : c)
					if (type.equals(vc.getType()))
						return vc;
			return null;
		}
	}

	ClientManager manager;

	private final EndpointConfig config;
	private final EndpointListener listener;
	private final Map<Integer, Byte> pvids;
	private final ScriptExchange scriptExchange;
	private final ViewContextMap viewContextMap = new ViewContextMap();

	public EndpointManagerImpl(EndpointConfig config, EndpointListener listener, Map<Integer, Byte> pvids)
			throws Exception {
		this.config = config;
		this.listener = listener;
		this.pvids = pvids;
		this.keepalive = config.keepAlive() ? new KeepAliveImpl() : hollowKeepAlive;
		this.manager = (ClientManager) Engine.add(config.getClientManagerConfig(), this, this);
		this.scriptExchange = config.getScriptEngineHandle() != null
				? new ScriptExchange(this, config.getScriptEngineHandle())
				: null;
		if (listener instanceof TunnelSupport) {
			((TunnelSupport) listener).registerTunnelSender(new TunnelSender() {
				@Override
				public void send(int providerid, int label, Octets data) throws InstantiationException,
						SizePolicyException, CodecException, ClassCastException {
					new Tunnel(providerid, 0, label, data).send(manager.getTransport());
				}
			});
		}
	}

	@Override
	public Transport getTransport() {
		return manager.getTransport();
	}

	@Override
	public void close() {
		if (Engine.remove(this))
			return;
		manager.close();
	}

	@Override
	public void close(Transport transport) {
		manager.close(transport);
	}

	@Override
	public void dispatch(Runnable r, Object hit) {
		((SupportDispatch) manager).dispatch(r, hit);
	}

	@Override
	public Listener getListener() {
		return listener;
	}

	@Override
	public Manager getWrapperManager() {
		return null;
	}

	@Override
	public void onManagerInitialized(Manager manager, Config config) {
		listener.onManagerInitialized(this, this.config);
	}

	@Override
	public void onManagerUninitialized(Manager manager) {
		if (LoginStatus.LOGINED_DONE == loginstatus) {
			try {
				keepalive.cancel();
				listener.onTransportRemoved(transportsaved);
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("listener.onTransportRemoved", e);
			}
		}
		if (LoginStatus.LOGINED_DONE == loginstatus || LoginStatus.LOGINED_NOTIFY == loginstatus) {
			Endpoint.clearDefaultEndpointManager(this);
			viewContextMap.clear();
			if (scriptExchange != null)
				scriptExchange.onUnload();
		}
		listener.onManagerUninitialized(this);
	}

	@Override
	public void onTransportAdded(Transport transport) throws Exception {
		listener.onSocketConnected();
		if (config.isPingServerOnly()) {
			keepalive.startPingAndKeepAlive(transport);
		} else {
			dhRandom = Helper.makeDHRandom();
			new CHandShake((byte) config.getDHGroup(), Octets
					.wrap(Helper.generateDHResponse(config.getDHGroup(), dhRandom).toByteArray()))
							.send(transport);
		}
	}

	@Override
	public void onTransportRemoved(Transport transport) throws Exception {
		if (keepalive.isTimeout())
			onErrorOccured(ErrorSource.ENDPOINT, ErrorCodes.ENDPOINT_PING_TIMEOUT);
		if (LoginStatus.LOGINING == loginstatus) {
			if (!config.isPingServerOnly())
				listener.onAbort(transport);
		} else
			transportsaved = transport;
	}

	private void onErrorOccured(int source, int code) throws Exception {
		listener.onErrorOccured(source, code, null);
	}

	@Override
	public void onAbort(Transport transport) throws Exception {
		listener.onAbort(transport);
	}

	@Override
	public Config getConfig() {
		return config;
	}

	@Override
	public long getSessionId() {
		return sessionid;
	}

	@Override
	public long getAccountFlags() {
		return accountflags;
	}

	@Override
	public String toString() {
		return getClass().getName();
	}

	PortForward.ManagerImpl getPortForwardManager() {
		final PortForward.ManagerImpl pfm;
		synchronized (this) {
			if (null == portforward)
				portforward = new PortForward.ManagerImpl(this);
			pfm = portforward;
		}
		return pfm;
	}

	private interface KeepAlive {

		void cancel();

		void startPingAndKeepAlive(Transport transport) throws Exception;

		void process(PingAndKeepAlive p);

		boolean isTimeout();

	}

	private static final KeepAlive hollowKeepAlive = new KeepAlive() {
		@Override
		public void cancel() {
		}

		@Override
		public void startPingAndKeepAlive(Transport transport) throws Exception {
		}

		@Override
		public void process(PingAndKeepAlive p) {
		}

		@Override
		public boolean isTimeout() {
			return false;
		}
	};

	private class KeepAliveImpl implements KeepAlive {
		private final static int PING_TIMEOUT = 5;
		private final static int KEEP_ALIVE_DELAY = 50;
		private final AtomicReference<Future<?>> refFuture = new AtomicReference<Future<?>>();
		private volatile boolean timeout = false;

		private void update(Future<?> newFuture) {
			Future<?> future = refFuture.getAndSet(newFuture);
			if (future != null)
				future.cancel(false);
		}

		@Override
		public void cancel() {
			update(null);
		}

		@Override
		public void startPingAndKeepAlive(Transport transport) throws Exception {
			new PingAndKeepAlive(System.currentTimeMillis()).send(transport);
			update(Engine.getProtocolScheduler().scheduleWithFixedDelay(new Runnable() {
				@Override
				public void run() {
					timeout = true;
					Transport transport = getTransport();
					if (transport != null)
						close(transport);
				}
			}, PING_TIMEOUT, Long.MAX_VALUE, TimeUnit.SECONDS));
		}

		@Override
		public void process(PingAndKeepAlive p) {
			if (timeout)
				return;
			listener.onKeepAlived((int) (System.currentTimeMillis() - p.timestamp));
			if (config.isPingServerOnly())
				return;
			update(Engine.getProtocolScheduler().scheduleWithFixedDelay(new Runnable() {
				@Override
				public void run() {
					try {
						startPingAndKeepAlive(getTransport());
					} catch (Exception e) {
					}
				}
			}, KEEP_ALIVE_DELAY, Long.MAX_VALUE, TimeUnit.SECONDS));
		}

		@Override
		public boolean isTimeout() {
			return timeout;
		}
	}

	private final KeepAlive keepalive;
	private volatile BigInteger dhRandom;
	private volatile PortForward.ManagerImpl portforward = null;
	private volatile Transport transportsaved = null;
	private volatile long sessionid = -1;
	private volatile long accountflags = 0;

	static enum LoginStatus {
		LOGINING, LOGINED_NOTIFY, LOGINED_DONE,
	}

	private volatile LoginStatus loginstatus = LoginStatus.LOGINING;

	void process(SHandShake p) throws Exception {
		StateTransport transport = (StateTransport) p.getTransport();
		byte[] material = Helper
				.computeDHKey(config.getDHGroup(), new BigInteger(p.dh_data.getBytes()), dhRandom)
				.toByteArray();
		byte[] key = ((InetSocketAddress) transport.getPeerAddress()).getAddress().getAddress();
		int half = material.length / 2;
		HmacMD5 mac = new HmacMD5(key, 0, key.length);
		mac.update(material, 0, half);
		transport.setOutputSecurityCodec(mac.digest(), p.c2sneedcompress);
		mac = new HmacMD5(key, 0, key.length);
		mac.update(material, half, material.length - half);
		transport.setInputSecurityCodec(mac.digest(), p.s2cneedcompress);
		transport.setState(limax.endpoint.states.Endpoint.EndpointSessionLogin);
		listener.onKeyExchangeDone();
		SessionLoginByToken protocol = new SessionLoginByToken();
		protocol.username = config.getLoginConfig().getUsername();
		protocol.token = config.getLoginConfig().getToken(new Octets(material));
		protocol.platflag = config.getLoginConfig().getPlatflag();
		ScriptEngineHandle seh = config.getScriptEngineHandle();
		if (seh != null) {
			StringBuilder dictionaryKeys = new StringBuilder(";");
			for (String v : seh.getDictionaryCache().keys())
				dictionaryKeys.append(v).append(',');
			dictionaryKeys.deleteCharAt(dictionaryKeys.length() - 1);
			protocol.platflag += dictionaryKeys;
		}
		protocol.pvids.putAll(pvids);
		InetSocketAddress address = (InetSocketAddress) transport.getLocalAddress();
		protocol.report_ip.replace(address.getAddress().getAddress());
		protocol.report_port = (short) address.getPort();
		protocol.send(transport);
		transport.setState(config.getEndpointState());
	}

	void process(SessionKick p) throws Exception {
		onErrorOccured(ErrorSource.LIMAX, p.error);
		if (scriptExchange != null)
			scriptExchange.onClose(p.error);
	}

	void process(limax.endpoint.switcherendpoint.PortForward p) throws Exception {
		if (null != portforward)
			portforward.onProtocol(p);
	}

	void process(PingAndKeepAlive p) throws Exception {
		keepalive.process(p);
	}

	void process(ProviderLogin p) throws Exception {
		ProviderLoginData logindata = null;
		ProviderLoginDataManager pldm = config.getLoginConfig().getProviderLoginDataManager();
		if (pldm != null)
			logindata = pldm.get(p.data.pvid);
		if (logindata == null)
			logindata = p.data;
		new ProviderLogin(logindata).send(p.getTransport());
	}

	void process(OnlineAnnounce p) throws Exception {
		final Transport transport = p.getTransport();
		if (ErrorSource.LIMAX == p.errorSource && ErrorCodes.SUCCEED == p.errorCode) {
			sessionid = p.sessionid;
			accountflags = p.flags;
			createStaticViewContextImpls();
			parseVariantDefines(p.variantdefines);
			if (scriptExchange != null)
				scriptExchange.onLoad(p.scriptdefines);
			loginstatus = LoginStatus.LOGINED_NOTIFY;
			Endpoint.setDefaultEndpointManager(this);
			if (p.lmkdata.size() > 0) {
				LmkUpdater lmkUpdater = config.getLoginConfig().getLmkUpdater();
				if (lmkUpdater != null)
					lmkUpdater.update(p.lmkdata, new Runnable() {
						@Override
						public void run() {
							try {
								new Tunnel(AuanyService.providerId, 0, -1, new Octets())
										.send(transport);
							} catch (Exception e) {
							}
						}
					});
			}
			listener.onTransportAdded(transport);
			loginstatus = LoginStatus.LOGINED_DONE;
			keepalive.startPingAndKeepAlive(transport);
		} else {
			onErrorOccured(p.errorSource, p.errorCode);
			close();
		}
	}

	private void parseVariantDefines(Map<Integer, VariantDefines> variantdefines) {
		for (Map.Entry<Integer, VariantDefines> e : variantdefines.entrySet()) {
			final int pvid = e.getKey();
			viewContextMap.put(pvid, VariantViewContextImpl.createInstance(pvid, e.getValue(), this));
		}
	}

	private void createStaticViewContextImpls() {
		limax.endpoint.auanyviews.ViewManager vm = limax.endpoint.auanyviews.ViewManager
				.createInstance(AuanyService.providerId);
		viewContextMap.put(vm.getProviderId(),
				new StaticViewContextImpl(vm.getProviderId(), vm.getClasses(), this));
		for (Map.Entry<Integer, Map<Short, Class<? extends View>>> e : config.getStaticViewClasses().entrySet())
			viewContextMap.put(e.getKey(), new StaticViewContextImpl(e.getKey(), e.getValue(), this));
	}

	void process(SyncViewToClients p) throws Exception {
		viewContextMap.onSyncViewToClients(p);
		if (scriptExchange != null)
			scriptExchange.onSyncViewToClients(p);
	}

	void process(Tunnel p) throws Exception {
		if (listener instanceof TunnelSupport)
			((TunnelSupport) listener).onTunnel(p.providerid, p.label, p.data);
		if (scriptExchange != null)
			scriptExchange.onTunnel(p.providerid, p.label, p.data);
	}

	@Override
	public ViewContext getViewContext(int pvid, Type type) {
		return viewContextMap.getViewContext(pvid, type);
	}

}
