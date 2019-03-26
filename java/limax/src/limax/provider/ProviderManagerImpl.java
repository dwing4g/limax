package limax.provider;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import limax.codec.Base64Decode;
import limax.codec.CodecException;
import limax.codec.JSON;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.defines.ErrorCodes;
import limax.defines.ProviderLoginData;
import limax.net.AbstractRpcContext;
import limax.net.Config;
import limax.net.Driver;
import limax.net.Engine;
import limax.net.Listener;
import limax.net.Manager;
import limax.net.ManagerConfig;
import limax.net.SizePolicyException;
import limax.net.Skeleton;
import limax.net.SupportDispatch;
import limax.net.SupportTypedDataBroadcast;
import limax.net.Transport;
import limax.net.WebSocketProtocol;
import limax.provider.providerendpoint.SendControlToServer;
import limax.provider.providerendpoint.SyncViewToClients;
import limax.provider.providerendpoint.Tunnel;
import limax.provider.switcherprovider.Bind;
import limax.provider.switcherprovider.BindResult;
import limax.provider.switcherprovider.Broadcast;
import limax.provider.switcherprovider.Dispatch;
import limax.provider.switcherprovider.JSONPublish;
import limax.provider.switcherprovider.LinkBroken;
import limax.provider.switcherprovider.OnlineAnnounce;
import limax.provider.switcherprovider.OnlineAnnounceAck;
import limax.provider.switcherprovider.Pay;
import limax.provider.switcherprovider.PayAck;
import limax.provider.switcherprovider.UnBind;
import limax.provider.switcherprovider.Unicast;
import limax.util.Dispatcher;
import limax.util.Enable;
import limax.util.Trace;

class ProviderManagerImpl extends AbstractRpcContext
		implements ProviderManager, SupportTypedDataBroadcast, SupportDispatch {
	private final ProviderListener listener;
	private final ProviderManagerConfig config;
	private final ProviderSwitcherExchanger exchanger;
	private final Set<Manager> managers = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final Manager wrapper;
	private final Dispatcher dispatcher;
	private final ViewContextImpl viewcontext;
	private boolean closed = false;
	private boolean hasbind = false;
	private final Map<Long, ProviderTransportImpl> transports = new ConcurrentHashMap<>();
	private final AtomicInteger sessionCounter = new AtomicInteger();
	private final Set<Transport> bindRetry = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private volatile Thread closingThread;
	private Future<?> jsonFuture;
	private final Future<?> keepAliveFuture;
	private final static LoginData flagDuplicateSession = new LoginData(new Octets(0));

	ProviderManagerImpl(ProviderManagerConfig config, ProviderListener listener, Manager wrapper) throws Exception {
		this.exchanger = new ProviderSwitcherExchanger(this);
		this.listener = listener;
		this.config = config;
		this.wrapper = wrapper;
		this.dispatcher = new Dispatcher(Engine.getProtocolExecutor());
		this.viewcontext = new ViewContextImpl(config, config.getViewManagerClassName()) {
			@Override
			void syncViewToClients(SyncViewToClients protocol)
					throws InstantiationException, SizePolicyException, CodecException, ClassCastException {
				toswitcher.syncViewToClients(protocol);
			}

			@Override
			void tunnel(long sessionid, URI group, int label, Octets data) throws TunnelException {
				try {
					toswitcher.tunnel(new Tunnel(config.getProviderId(), sessionid, label,
							config.getTunnelEncoder(group).transform(label, data)));
				} catch (InstantiationException | ClassCastException | SizePolicyException | CodecException e) {
					throw new TunnelException(TunnelException.Type.NETWORK, e);
				}
			}

			@Override
			ViewSession getViewSession(long sessionid) {
				ProviderTransportImpl transport = transports.get(sessionid);
				return transport == null ? null : transport.getViewSession();
			}
		};
		checkSupported();
		try {
			for (ManagerConfig cfg : config.getManagerConfigs())
				managers.add(exchanger.add(cfg));
		} catch (Exception e) {
			managers.forEach(Manager::close);
			throw e;
		}
		listener.onManagerInitialized(this, config);
		long keepAliveTimeout = Engine.getIntranetKeepAliveTimeout();
		this.keepAliveFuture = keepAliveTimeout > 0 ? Engine.getProtocolScheduler().scheduleWithFixedDelay(() -> {
			try {
				exchanger.send(new limax.provider.switcherprovider.KeepAlive(keepAliveTimeout));
			} catch (Exception e) {
			}
		}, 0, keepAliveTimeout / 2, TimeUnit.MILLISECONDS) : null;
	}

	boolean isStateless() {
		return config.getSessionTimeout() > 0;
	}

	private void checkSupported() {
		if (Enable.True == config.getAllowUseScript() && !viewcontext.isScriptSupported())
			throw new RuntimeException("config allowUseScript = true but viewcontext.isScriptSupported = false");
		if (Enable.True == config.getAllowUseVariant() && !viewcontext.isVariantSupported())
			throw new RuntimeException("config allowUseVariant = true but viewcontext.isVariantSupported = false");
		if (Trace.isWarnEnabled()) {
			if (Enable.False == config.getAllowUseScript() && viewcontext.isScriptSupported())
				Trace.warn("config allowUseScript = false but viewcontext.isScriptSupported = true");
			if (Enable.False == config.getAllowUseVariant() && viewcontext.isVariantSupported())
				Trace.warn("config allowUseVariant = false but viewcontext.isVariantSupported = true");
		}
	}

	private void scheduleSessionTask(long sessionid, Runnable task) {
		Engine.getApplicationExecutor().execute(sessionid, task);
	}

	static {
		Engine.registerDriver(new Driver() {
			@Override
			public Class<? extends Config> getConfigClass() {
				return ProviderManagerConfig.class;
			}

			@Override
			public Class<? extends Listener> getListenerClass() {
				return ProviderListener.class;
			}

			@Override
			public Manager newInstance(Config config, Listener listener, Manager wrapper) throws Exception {
				return new ProviderManagerImpl((ProviderManagerConfig) config, (ProviderListener) listener, wrapper);
			}
		});
	}

	@Override
	public void close() {
		synchronized (this) {
			closed = true;
		}
		if (Engine.remove(this))
			return;
		if (jsonFuture != null)
			jsonFuture.cancel(false);
		closingThread = Thread.currentThread();
		exchanger.close();
		managers.forEach(Manager::close);
		dispatcher.await();
		viewcontext.close();
		while (sessionCounter.get() != 0)
			LockSupport.park(sessionCounter);
		if (keepAliveFuture != null)
			keepAliveFuture.cancel(true);
		listener.onManagerUninitialized(this);
	}

	@Override
	public void close(long sessionid, int reason) {
		exchanger.kick(sessionid, reason);
		removeTransport1(sessionid);
	}

	@Override
	public void close(long sessionid) {
		close(sessionid, ErrorCodes.PROVIDER_KICK_SESSION);
	}

	@Override
	public void close(ProviderTransport transport, int reason) {
		close(transport.getSessionId(), reason);
	}

	@Override
	public void close(Transport _transport) {
		ProviderTransport transport = (ProviderTransport) _transport;
		int reason = transport.getLoginData() == flagDuplicateSession ? ErrorCodes.PROVIDER_DUPLICATE_SESSION
				: ErrorCodes.PROVIDER_KICK_SESSION;
		close(transport, reason);
	}

	@Override
	public ProviderListener getListener() {
		return listener;
	}

	@Override
	public Config getConfig() {
		return config;
	}

	int getPvid() {
		return config.getProviderId();
	}

	@Override
	public String toString() {
		return config.getName();
	}

	private ProviderTransportImpl _removeTransport(long sessionid) {
		ProviderTransportImpl transport = transports.remove(sessionid);
		if (transport != null)
			exchanger.removeSession(transport.getRelativeTransport(), sessionid);
		return transport;
	}

	void statelessRemoveTransport(long sessionid) {
		scheduleSessionTask(sessionid, () -> {
			ProviderTransportImpl transport = _removeTransport(sessionid);
			if (transport == null)
				return;
			transport.updateFuture(null);
			transport.getViewSession().close(sessionid, false);
			if (sessionCounter.decrementAndGet() == 0)
				LockSupport.unpark(closingThread);
		});
	}

	void removeTransport1(long sessionid) {
		scheduleSessionTask(sessionid, () -> removeTransport2(sessionid));
	}

	private void removeTransport2(long sessionid) {
		ProviderTransportImpl transport = _removeTransport(sessionid);
		if (transport == null)
			return;
		Engine.getProtocolExecutor().execute(sessionid, () -> {
			transport.getViewSession().close(sessionid, true);
			scheduleSessionTask(sessionid, () -> {
				try {
					listener.onTransportRemoved(transport);
				} catch (Throwable e) {
					if (Trace.isErrorEnabled())
						Trace.error(this + " removeTransport = " + transport, e);
				} finally {
					if (sessionCounter.decrementAndGet() == 0)
						LockSupport.unpark(closingThread);
				}
			});
		});
	}

	private void addTransport1(OnlineAnnounce oa) {
		ProviderTransportImpl transport = transports.get(oa.sessionid);
		if (null == transport) {
			addTransport3(oa);
		} else {
			try {
				transport.setLoginData(flagDuplicateSession);
				listener.onTransportDuplicate(transport);
			} catch (Throwable e) {
				close(transport, ErrorCodes.PROVIDER_DUPLICATE_SESSION);
			} finally {
				transport.setLoginData(null);
			}
			scheduleSessionTask(oa.sessionid, () -> addTransport2(oa));
		}
	}

	private void addTransport2(OnlineAnnounce oa) {
		if (!transports.containsKey(oa.sessionid)) {
			Engine.getProtocolExecutor().execute(oa.sessionid,
					() -> scheduleSessionTask(oa.sessionid, () -> addTransport3(oa)));
		} else {
			try {
				new OnlineAnnounceAck(oa.sessionid, ErrorCodes.PROVIDER_SESSION_LOGINED).send(oa.getTransport());
			} catch (Throwable e) {
				if (Trace.isInfoEnabled())
					Trace.info(this + " addTransport2 = " + oa.getTransport(), e);
			}
		}
	}

	private void addTransport3(OnlineAnnounce oa) {
		if (!exchanger.addSession(oa.getTransport(), oa.sessionid))
			return;
		ProviderTransportImpl transport = new ProviderTransportImpl(this, oa);
		transports.put(oa.sessionid, transport);
		try {
			switch (oa.logindata.type) {
			case ProviderLoginData.tTunnelData:
				transport.setLoginData(new LoginData(oa.logindata.label,
						config.getTunnelDecoder().transform(oa.logindata.label, oa.logindata.data)));
				break;
			case ProviderLoginData.tUserData:
				transport.setLoginData(new LoginData(oa.logindata.data));
			}
			listener.onTransportAdded(transport);
			transport.setLoginData(null);
			sessionCounter.incrementAndGet();
		} catch (Throwable e) {
			if (Trace.isErrorEnabled())
				Trace.error(this + " addTransport3 = " + transport, e);
			_removeTransport(oa.sessionid);
			try {
				new OnlineAnnounceAck(oa.sessionid, ErrorCodes.PROVIDER_ADD_TRANSPORT_EXCEPTION)
						.send(oa.getTransport());
			} catch (Throwable e2) {
				if (Trace.isInfoEnabled())
					Trace.info(this + " addTransport3 = " + oa.getTransport(), e2);
			}
			return;
		}
		try {
			viewcontext.onSessionLogin(oa.sessionid, transport.getViewSession());
			new OnlineAnnounceAck(oa.sessionid, ErrorCodes.SUCCEED).send(oa.getTransport());
		} catch (Throwable e) {
			if (Trace.isErrorEnabled())
				Trace.error(this + " addTransport3 = " + transport, e);
			removeTransport2(oa.sessionid);
		}
	}

	private void statelessResetSessionTimeout(ProviderTransportImpl transport, long sessionid) {
		transport.updateFuture(Engine.getProtocolScheduler().schedule(() -> statelessRemoveTransport(sessionid),
				config.getSessionTimeout(), TimeUnit.MILLISECONDS));
	}

	private void statelessAddTransport(long sessionid, Transport from) {
		ProviderTransportImpl transport = transports.get(sessionid);
		if (null != transport) {
			statelessResetSessionTimeout(transport, sessionid);
			return;
		}
		if (!exchanger.addSession(from, sessionid))
			return;
		transports.put(sessionid, transport = new ProviderTransportImpl(this, sessionid, from));
		try {
			sessionCounter.incrementAndGet();
			viewcontext.onSessionLogin(sessionid, transport.getViewSession());
			statelessResetSessionTimeout(transport, sessionid);
		} catch (Throwable e) {
			if (Trace.isErrorEnabled())
				Trace.error(this + " addTransport = " + transport, e);
			statelessRemoveTransport(sessionid);
		}
	}

	@Override
	public void dispatch(Runnable r, Object hit) {
		if (r instanceof Dispatch) {
			Dispatch protocol = (Dispatch) r;
			if (isStateless())
				statelessAddTransport(protocol.sessionid, protocol.getTransport());
			providerdispatcher.dispatchSessionProtocol(protocol.sessionid, protocol.ptype, protocol.pdata);
		} else if (r instanceof SendControlToServer) {
			SendControlToServer protocol = (SendControlToServer) r;
			if (isStateless())
				statelessAddTransport(protocol.sessionid, protocol.getTransport());
			providerdispatcher.onViewControl(protocol);
		} else if (r instanceof Tunnel) {
			Tunnel protocol = (Tunnel) r;
			if (isStateless())
				statelessAddTransport(protocol.sessionid, protocol.getTransport());
			providerdispatcher.onTunnel(protocol);
		} else
			dispatcher.execute(r, hit);
	}

	@Override
	public void broadcast(int ptype, Octets pdata)
			throws InstantiationException, SizePolicyException, CodecException, ClassCastException {
		toswitcher.broadcastToSwitcher(ptype | (config.getProviderId() << 8), pdata);
	}

	void send(long sessionid, int ptype, Octets pdata)
			throws InstantiationException, SizePolicyException, CodecException, ClassCastException {
		toswitcher.unicastToSwitcher(sessionid, ptype | (config.getProviderId() << 8), pdata);
	}

	@Override
	public synchronized boolean isListening() {
		return hasbind;
	}

	private Bind createBindProtocol() {
		Bind bind = new Bind();
		bind.pvid = config.getProviderId();
		bind.pvkey = config.getProviderKey();
		bind.pinfos.putAll(config.getProviderProtocolInfos());
		bind.capability = 0;
		if (viewcontext.isVariantEnabled()) {
			bind.variantdefines = viewcontext.getVariantDefines();
			bind.capability |= Bind.PS_VARIANT_ENABLED;
		}
		if (viewcontext.isScriptEnabled()) {
			bind.scriptdefines = viewcontext.getScriptDefines();
			bind.capability |= Bind.PS_SCRIPT_ENABLED;
		}
		if (viewcontext.isScriptSupported())
			bind.capability |= Bind.PS_SCRIPT_SUPPORTED;
		if (viewcontext.isVariantSupported())
			bind.capability |= Bind.PS_VARIANT_SUPPORTED;
		if (isStateless())
			bind.capability |= Bind.PS_STATELESS;
		if (listener instanceof PaySupport)
			bind.capability |= Bind.PS_PAY_SUPPORTED;
		if (listener instanceof LoginDataSupport)
			bind.capability |= Bind.PS_LOGINDATA_SUPPORTED;
		if (listener instanceof JSONPublisher)
			try {
				bind.json = JSON.stringify(((JSONPublisher) listener).getJSON());
			} catch (Exception e) {
			}
		return bind;
	}

	synchronized void tryBind(Transport transport) throws Exception {
		if (!hasbind || closed)
			return;
		createBindProtocol().send(transport);
	}

	@Override
	public synchronized void openListen() throws IOException {
		if (closed)
			throw new IOException("Provider closed");
		if (hasbind)
			return;
		if (1 == config.getManagerConfigs().size())
			DirectDispatcher.getInstance().register(config.getProviderId(), providerdispatcher);
		try {
			exchanger.send(createBindProtocol());
		} catch (CodecException e) {
			throw new IOException(e);
		}
		hasbind = true;
	}

	@Override
	public synchronized void closeListen() throws IOException {
		if (closed)
			throw new IOException("Provider closed");
		if (!hasbind)
			return;
		if (jsonFuture != null) {
			jsonFuture.cancel(false);
			jsonFuture = null;
		}
		DirectDispatcher.getInstance().unregister(config.getProviderId());
		toswitcher = createDefaultToSwitcher();
		try {
			exchanger.send(new UnBind());
		} catch (Exception e) {
			throw new IOException(e);
		}
		hasbind = false;
	}

	private synchronized void flushJSON(JSONPublisher p, long JSONPublishDelayMin) {
		if (!closed)
			jsonFuture = Engine.getProtocolScheduler().schedule(() -> {
				try {
					exchanger.sendAny(new JSONPublish(JSON.stringify(p.getJSON())));
				} catch (Exception e) {
					if (Trace.isErrorEnabled())
						Trace.error("flushJSON", e);
				} finally {
					flushJSON(p, JSONPublishDelayMin);
				}
			}, Long.max(JSONPublishDelayMin, p.getDelay()), TimeUnit.MILLISECONDS);
	}

	final void process(BindResult p) {
		Transport transport = p.getTransport();
		if (p.error != ErrorCodes.SUCCEED) {
			if (p.error == ErrorCodes.PROVIDER_DUPLICATE_ID && bindRetry.add(transport)) {
				long keepAliveTimeout = Engine.getIntranetKeepAliveTimeout();
				if (keepAliveTimeout <= 0)
					keepAliveTimeout = 5000;
				if (Trace.isWarnEnabled())
					Trace.warn("provider openlisten failed, pvid = " + config.getProviderId() + " error = " + p.error
							+ "(PROVIDER_DUPLICATE_ID) and retry " + keepAliveTimeout + " milliseconds later");
				Engine.getProtocolScheduler().schedule(() -> {
					try {
						createBindProtocol().send(transport);
					} catch (Exception e) {
						Trace.fatal("provider openlisten retry failed, pvid = " + config.getProviderId(), e);
						System.exit(0);
					}
				}, keepAliveTimeout, TimeUnit.MILLISECONDS);
				return;
			}
			Trace.fatal("provider openlisten failed, pvid = " + config.getProviderId() + " error = " + p.error);
			System.exit(0);
		}
		bindRetry.remove(transport);
		if (listener instanceof JSONPublisher && jsonFuture == null && p.jsonPublishDelayMin > 0)
			flushJSON((JSONPublisher) listener, p.jsonPublishDelayMin);
		if (Trace.isInfoEnabled())
			Trace.info("provider had bind success! pvid = " + config.getProviderId());
	}

	final void process(LinkBroken protocol) {
		if (Trace.isInfoEnabled())
			Trace.info("process LinkBroken sessionid = " + protocol.sessionid);
		removeTransport1(protocol.sessionid);
	}

	final void process(OnlineAnnounce oa) {
		if (!isStateless())
			scheduleSessionTask(oa.sessionid, () -> addTransport1(oa));
	}

	final void process(SendControlToServer protocol) {
		View view = viewcontext.findView(protocol.sessionid, protocol.classindex, protocol.instanceindex);
		if (view != null) {
			if (protocol.controlindex != -1)
				view.scheduleProcessControl(protocol.controlindex, OctetsStream.wrap(protocol.controlparameter),
						protocol.sessionid);
			else
				view.scheduleOnMessage(protocol.stringdata, protocol.sessionid);
		} else if (Trace.isInfoEnabled())
			Trace.info("view not found " + protocol);
	}

	private void process(long sessionid, short classindex, int instanceindex, String message) {
		View view = viewcontext.findView(sessionid, classindex, instanceindex);
		if (view != null)
			view.scheduleOnMessage(message, sessionid);
		else if (Trace.isInfoEnabled())
			Trace.info("view not found classindex = " + classindex + " instanceindex = " + instanceindex + " message = "
					+ message);
	}

	private void onTunnel(long sessionid, int label, Octets data) throws Exception {
		Octets tunnelData;
		try {
			tunnelData = config.getTunnelDecoder().transform(label, data);
		} catch (TunnelException e) {
			((TunnelSupport) listener).onException(sessionid, label, e);
			return;
		}
		((TunnelSupport) listener).onTunnel(sessionid, label, tunnelData);
	}

	final void process(Tunnel protocol) {
		scheduleSessionTask(protocol.sessionid, () -> {
			try {
				onTunnel(protocol.sessionid, protocol.label, protocol.data);
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("ProviderListener.onTunnel protocol = " + protocol, e);
				close(protocol.sessionid, ErrorCodes.PROVIDER_TUNNEL_EXCEPTION);
			}
		});
	}

	private void process(long sessionid, int label, String message) {
		scheduleSessionTask(sessionid, () -> {
			try {
				onTunnel(sessionid, label,
						Octets.wrap(Base64Decode.transform(message.getBytes(StandardCharsets.ISO_8859_1))));
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("ProviderListener.onTunnel message = " + message, e);
				close(sessionid, ErrorCodes.PROVIDER_TUNNEL_EXCEPTION);
			}
		});
	}

	final void process(Pay pay) {
		scheduleSessionTask(pay.sessionid, () -> {
			try {
				((PaySupport) listener).onPay(pay.serial, pay.sessionid, pay.product, pay.price, pay.count, () -> {
					try {
						new PayAck(config.getProviderId(), pay.serial).send(pay.getTransport());
					} catch (Exception e) {
					}
				});
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("PaySupport.accept", e);
			}
		});
	}

	final void process(PayAck ack) {
		Engine.getApplicationExecutor().execute(() -> {
			try {
				((PaySupport) listener).onPayConfirm(ack.serial, () -> {
					try {
						new PayAck(config.getProviderId(), ack.serial).send(ack.getTransport());
					} catch (Exception e) {
					}
				});
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("PaySupport.cleanup", e);
			}
		});
	}

	private interface ToSwitcher {
		void unicastToSwitcher(long sessionid, int ptype, Octets pdata)
				throws InstantiationException, SizePolicyException, CodecException, ClassCastException;

		void broadcastToSwitcher(int ptype, Octets pdata)
				throws InstantiationException, SizePolicyException, CodecException, ClassCastException;

		void syncViewToClients(SyncViewToClients protocol)
				throws InstantiationException, SizePolicyException, CodecException, ClassCastException;

		void tunnel(Tunnel protocol)
				throws InstantiationException, SizePolicyException, CodecException, ClassCastException;
	}

	private final ToSwitcher createDefaultToSwitcher() {
		return new ToSwitcher() {
			@Override
			public void unicastToSwitcher(long sessionid, int ptype, Octets pdata)
					throws InstantiationException, SizePolicyException, CodecException, ClassCastException {
				try {
					exchanger.send(new Unicast(sessionid, ptype, pdata), sessionid);
				} catch (InstantiationException | SizePolicyException | CodecException | ClassCastException e) {
					throw e;
				} catch (Throwable e) {
					throw new CodecException(e);
				}
			}

			@Override
			public void broadcastToSwitcher(int ptype, Octets pdata)
					throws InstantiationException, SizePolicyException, CodecException, ClassCastException {
				exchanger.send(new Broadcast(ptype, pdata));
			}

			@Override
			public void syncViewToClients(SyncViewToClients protocol)
					throws InstantiationException, SizePolicyException, CodecException, ClassCastException {
				exchanger.send(protocol, protocol.sessionids);
			}

			@Override
			public void tunnel(Tunnel protocol)
					throws InstantiationException, SizePolicyException, CodecException, ClassCastException {
				exchanger.send(protocol);
			}
		};
	}

	private volatile ToSwitcher toswitcher = createDefaultToSwitcher();
	private final DirectDispatcher.ProviderDispatchable providerdispatcher = new DirectDispatcher.ProviderDispatchable() {
		private void _dispatchSessionProtocol(final long sessionid, int ptype, Octets pdata) {
			ProviderTransportImpl transport = transports.get(sessionid);
			if (null == transport)
				return;
			Skeleton skel;
			try {
				skel = config.getProviderState().decode(ptype & 0xff, pdata, transport);
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("Provider decode protocol sessionid = " + sessionid, e);
				close(transport);
				return;
			}
			try {
				skel.process();
			} catch (Throwable e) {
				if (Trace.isErrorEnabled())
					Trace.error("Provider skel.process() sessionid = " + sessionid, e);
				close(transport);
			}
		}

		private void _dispatchWebSocketProtocol(final long sessionid, Octets pdata) {
			ProviderTransportImpl transport = transports.get(sessionid);
			if (null == transport)
				return;
			try {
				WebSocketProtocol wsp = new WebSocketProtocol(OctetsStream.wrap(pdata)) {
					@Override
					public void process() throws Exception {
					}
				};
				String s = wsp.getText();
				int p0 = s.indexOf(',') + 1;
				int p1 = s.indexOf(',', p0);
				int classindexOrLabel = Integer.parseInt(s.substring(p0, p1), Character.MAX_RADIX);
				int p2 = s.indexOf(':', ++p1);
				if (p2 == -1) {
					process(sessionid, classindexOrLabel, s.substring(p1));
				} else {
					int instanceindex = Integer.parseInt(s.substring(p1, p2), Character.MAX_RADIX);
					String message = s.substring(++p2);
					process(sessionid, (short) classindexOrLabel, instanceindex, message);
				}
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("Provider decode WebSocketProtocol sessionid = " + sessionid, e);
				close(transport);
			}
		}

		@Override
		public final void dispatchSessionProtocol(final long sessionid, final int ptype, final Octets pdata) {
			if (-1 == ptype) {
				_dispatchWebSocketProtocol(sessionid, pdata);
			} else {
				scheduleSessionTask(sessionid, () -> _dispatchSessionProtocol(sessionid, ptype, pdata));
			}
		}

		@Override
		public final void setSwitcherReceiveable(final DirectDispatcher.SwitcherReceiveable recvable) {
			toswitcher = new ToSwitcher() {
				@Override
				public void unicastToSwitcher(long sessionid, int ptype, Octets pdata) {
					recvable.switcherUnicast(sessionid, ptype, pdata);
				}

				@Override
				public void broadcastToSwitcher(int ptype, Octets pdata) {
					recvable.switcherBroadcast(ptype, pdata);
				}

				@Override
				public void syncViewToClients(SyncViewToClients protocol) {
					recvable.syncViewToClients(protocol);
				}

				@Override
				public void tunnel(Tunnel protocol) {
					recvable.tunnel(protocol);
				}
			};
		}

		@Override
		public void onViewControl(final SendControlToServer protocol) {
			process(protocol);
		}

		@Override
		public void onTunnel(final Tunnel protocol) {
			process(protocol);
		}
	};

	@Override
	public Manager getWrapperManager() {
		return wrapper;
	}

	void check(int type, int size) throws InstantiationException, SizePolicyException {
		config.getProviderState().check(type & 0xff, size);
	}

	Manager getOutmostWrapperManager() {
		Manager manager = this;
		while (manager.getWrapperManager() != null)
			manager = manager.getWrapperManager();
		return manager;
	}
}
