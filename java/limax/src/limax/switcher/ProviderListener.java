package limax.switcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import limax.codec.Octets;
import limax.defines.ErrorCodes;
import limax.defines.ProviderLoginData;
import limax.defines.SessionType;
import limax.defines.VariantDefines;
import limax.net.Config;
import limax.net.Engine;
import limax.net.Manager;
import limax.net.Protocol;
import limax.net.ServerListener;
import limax.net.SizePolicyException;
import limax.net.Transport;
import limax.net.WebSocketProtocol;
import limax.net.WebSocketTransport;
import limax.provider.DirectDispatcher;
import limax.provider.providerendpoint.SendControlToServer;
import limax.provider.providerendpoint.Tunnel;
import limax.switcher.switcherauany.CheckProviderKey;
import limax.switcher.switcherauany.OnlineAnnounce;
import limax.switcher.switcherauany.ProviderDown;
import limax.switcher.switcherendpoint.ProviderLogin;
import limax.switcher.switcherprovider.Bind;
import limax.switcher.switcherprovider.BindResult;
import limax.switcher.switcherprovider.Dispatch;
import limax.switcher.switcherprovider.LinkBroken;
import limax.switcher.switcherprovider.Probe;
import limax.switcher.switcherprovider.UnBind;
import limax.util.Trace;

public final class ProviderListener implements ServerListener {
	private volatile Future<?> keepAliveFuture;
	private static final ProviderListener instance = new ProviderListener();

	public static ProviderListener getInstance() {
		return instance;
	}

	private ProviderListener() {
	}

	private volatile Manager manager;

	private final Map<Integer, Transport> pvidmap = new HashMap<>();

	private static class SessionObject implements ProviderArgs {
		private final int pvid;
		private final Map<Integer, Integer> pinfos;
		private final Set<Long> sids = new HashSet<>();
		private final VariantDefines variantDefines;
		private final String scriptDefines;
		private final String scriptDefinesKey;
		private final int capability;
		private volatile String json = "";

		SessionObject(int pvid, Map<Integer, Integer> pinfos, int capability, VariantDefines variantDefines,
				String scriptDefines) {
			this.pvid = pvid;
			this.pinfos = pinfos;
			this.capability = capability;
			this.variantDefines = isVariantEnabled() ? variantDefines : null;
			if (isScriptEnabled()) {
				this.scriptDefines = scriptDefines;
				int pos = scriptDefines.indexOf(":");
				int end = pos + 1 + Integer.parseInt(scriptDefines.substring(1, pos), Character.MAX_RADIX);
				this.scriptDefinesKey = scriptDefines.substring(scriptDefines.lastIndexOf(',', end) + 1, end);
			} else {
				this.scriptDefines = null;
				this.scriptDefinesKey = null;
			}
		}

		private boolean capability(int mask) {
			return mask == (capability & mask);
		}

		@Override
		public boolean isVariantEnabled() {
			return capability(Bind.PS_VARIANT_ENABLED);
		}

		@Override
		public boolean isVariantSupported() {
			return capability(Bind.PS_VARIANT_SUPPORTED);
		}

		@Override
		public boolean isScriptEnabled() {
			return capability(Bind.PS_SCRIPT_ENABLED);
		}

		@Override
		public boolean isScriptSupported() {
			return capability(Bind.PS_SCRIPT_SUPPORTED);
		}

		@Override
		public boolean isStateless() {
			return capability(Bind.PS_STATELESS);
		}

		@Override
		public boolean isPaySupported() {
			return capability(Bind.PS_PAY_SUPPORTED);
		}

		@Override
		public boolean isLoginDataSupported() {
			return capability(Bind.PS_LOGINDATA_SUPPORTED);
		}

		@Override
		public VariantDefines getVariantDefines() {
			return variantDefines;
		}

		@Override
		public String getScriptDefines() {
			return scriptDefines;
		}

		@Override
		public String getScriptDefinesKey() {
			return scriptDefinesKey;
		}

		@Override
		public int getProviderId() {
			return pvid;
		}

		@Override
		public String toString() {
			return "pvid=" + pvid;
		}
	}

	@Override
	public void onManagerInitialized(Manager manager, Config config) {
		this.manager = manager;
		long keepAliveTimeout = Engine.getIntranetKeepAliveTimeout();
		if (keepAliveTimeout > 0)
			keepAliveFuture = Engine.getProtocolScheduler().scheduleWithFixedDelay(() -> {
				Collection<Transport> transports;
				synchronized (this) {
					transports = new ArrayList<>(pvidmap.values());
				}
				limax.switcher.switcherprovider.KeepAlive p = new limax.switcher.switcherprovider.KeepAlive(
						keepAliveTimeout);
				for (Transport transport : transports) {
					try {
						p.send(transport);
					} catch (Exception e) {
					}
				}
			}, 0, keepAliveTimeout / 2, TimeUnit.MILLISECONDS);
	}

	@Override
	public void onManagerUninitialized(Manager manager) {
		if (keepAliveFuture != null)
			keepAliveFuture.cancel(true);
	}

	@Override
	public void onTransportAdded(Transport transport) {
		if (Trace.isInfoEnabled())
			Trace.info(manager + " addTransport " + transport);
	}

	@Override
	public void onTransportRemoved(Transport transport) {
		if (Trace.isInfoEnabled())
			Trace.info(manager + " removeTransport " + transport, transport.getCloseReason());
		doUnBind(transport);
	}

	synchronized ProviderArgs getProviderArgs(int pvid) {
		Transport transport = pvidmap.get(pvid);
		return transport == null ? null : (SessionObject) transport.getSessionObject();
	}

	private final Map<Transport, CheckProviderKey> pending = new IdentityHashMap<>();

	public final synchronized void processResult(CheckProviderKey rpc) {
		final Bind bind = (Bind) rpc.getNote();
		if (bind == null)
			return;
		rpc.setNote(null);
		final Transport transport = bind.getTransport();
		final SessionObject so;
		try {
			pending.remove(transport);
			int error = rpc.getResult().error;
			if (error != ErrorCodes.SUCCEED) {
				new BindResult(error, 0).send(transport);
				return;
			}
			Transport prevTransport = pvidmap.get(bind.pvid);
			if (prevTransport != null) {
				Probe probe = new Probe();
				probe.getArgument().key = System.currentTimeMillis();
				try {
					probe.send(prevTransport);
					if (Trace.isErrorEnabled())
						Trace.error(manager + " Transport " + transport + " closed. Duplicate pvid = " + bind.pvid
								+ ", probe send on Transport " + prevTransport);
					try {
						new BindResult(ErrorCodes.PROVIDER_DUPLICATE_ID, 0).send(transport);
					} catch (Exception e) {
						if (Trace.isErrorEnabled())
							Trace.error(manager + " send protocol exception", e);
						manager.close(transport);
					}
					return;
				} catch (Exception e) {
					manager.close(prevTransport);
				}
			}
			transport.setSessionObject(so = new SessionObject(bind.pvid, bind.pinfos, bind.capability,
					bind.variantdefines, bind.scriptdefines));
			pvidmap.put(bind.pvid, transport);
			new BindResult(ErrorCodes.SUCCEED, rpc.getResult().jsonPublishDelayMin).send(transport);
			Trace.fatal(manager + " bind " + bind.pvid);
		} catch (Exception e) {
			if (Trace.isErrorEnabled())
				Trace.error(manager + " send protocol exception", e);
			manager.close(transport);
			return;
		}
		final DirectDispatcher.ProviderDispatchable dispatch = DirectDispatcher.getInstance()
				.getProviderDispatchable(bind.pvid);
		if (null == dispatch)
			DirectDispatcher.getInstance().register(bind.pvid, createProviderDispatchable(transport, so));
		else
			dispatch.setSwitcherReceiveable(createSwitcherReceiveable(so));
	}

	public final void processTimeout(CheckProviderKey rpc) {
		try {
			rpc.send(AuanyClientListener.getInstance().getTransport());
		} catch (Exception e) {
		}
	}

	public final void processResult(Probe probe) {
		if (probe.getArgument().key != probe.getResult().key)
			processTimeout(probe);
	}

	public final void processTimeout(Probe probe) {
		manager.close(probe.getTransport());
	}

	synchronized void auanyOnline(Transport transport) {
		try {
			OnlineAnnounce p = SwitcherListener.getInstance().createOnlineAnnounce();
			List<limax.switcher.switcherauany.JSONPublish> jsons = new ArrayList<>();
			pvidmap.forEach((pvid, t) -> {
				SessionObject so = (SessionObject) t.getSessionObject();
				p.pvids.put(pvid, so.isPaySupported());
				if (!so.json.isEmpty())
					jsons.add(new limax.switcher.switcherauany.JSONPublish(pvid, so.json));
			});
			p.send(transport);
			for (limax.switcher.switcherauany.JSONPublish json : jsons)
				json.send(transport);
		} catch (Exception e) {
		}
		pending.values().forEach(rpc -> {
			try {
				rpc.send(transport);
			} catch (Exception e) {
			}
		});
	}

	void doUnBind(Transport transport) {
		final SessionObject so;
		synchronized (this) {
			pending.remove(transport);
			so = (SessionObject) transport.getSessionObject();
			if (so == null)
				return;
			pvidmap.remove(so.pvid);
			transport.setSessionObject(null);
			Trace.fatal(manager + " unbind " + so.pvid);
		}
		try {
			new ProviderDown(so.pvid).send(AuanyClientListener.getInstance().getTransport());
		} catch (Exception e) {
		}
		DirectDispatcher.getInstance().unregister(so.pvid);
		so.sids.forEach(
				sid -> SwitcherListener.getInstance().kickSession(so.pvid, sid, ErrorCodes.SWITCHER_PROVIDER_UNBIND));
	}

	public final void process(Bind bind) {
		CheckProviderKey rpc = new CheckProviderKey();
		rpc.getArgument().pvid = bind.pvid;
		rpc.getArgument().pvkey = bind.pvkey;
		rpc.getArgument().paySupported = (bind.capability & Bind.PS_PAY_SUPPORTED) != 0;
		rpc.getArgument().json = bind.json;
		rpc.setNote(bind);
		synchronized (this) {
			pending.put(bind.getTransport(), rpc);
		}
		try {
			rpc.send(AuanyClientListener.getInstance().getTransport());
		} catch (Exception e) {
		}
	}

	public final void process(UnBind protocol) {
		doUnBind(protocol.getTransport());
	}

	public final void process(limax.switcher.switcherauany.Pay pay) {
		try {
			new limax.switcher.switcherprovider.Pay(pay.payid, pay.serial, pay.sessionid, pay.product, pay.price,
					pay.count).send(pvidmap.get(pay.payid));
		} catch (Exception e) {
		}
	}

	public final void process(limax.switcher.switcherauany.PayAck ack) {
		try {
			new limax.switcher.switcherprovider.PayAck(ack.payid, ack.serial).send(pvidmap.get(ack.payid));
		} catch (Exception e) {
		}
	}

	public final void process(limax.switcher.switcherprovider.PayAck ack) {
		try {
			new limax.switcher.switcherauany.PayAck(ack.payid, ack.serial)
					.send(AuanyClientListener.getInstance().getTransport());
		} catch (Exception e) {
		}
	}

	public final void process(limax.switcher.switcherprovider.JSONPublish p) {
		try {
			SessionObject so = ((SessionObject) p.getTransport().getSessionObject());
			new limax.switcher.switcherauany.JSONPublish(so.pvid, so.json = p.json)
					.send(AuanyClientListener.getInstance().getTransport());
		} catch (Exception e) {
		}
	}

	private DirectDispatcher.ProviderDispatchable createProviderDispatchable(Transport transport, SessionObject so) {
		return new DirectDispatcher.ProviderDispatchable() {
			@Override
			public void dispatchSessionProtocol(long sid, int ptype, Octets pdata) {
				try {
					new Dispatch(sid, ptype, pdata).send(transport);
				} catch (Exception e) {
					if (Trace.isInfoEnabled())
						Trace.info("Switcher send dispatch exception, kickSession sessionid = " + sid, e);
					if (so.sids.remove(sid))
						SwitcherListener.getInstance().closeSession(sid, ErrorCodes.SWITCHER_SEND_DISPATCH_EXCEPTION);
				}
			}

			@Override
			public void setSwitcherReceiveable(DirectDispatcher.SwitcherReceiveable recvable) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void onViewControl(SendControlToServer protocol) {
				try {
					protocol.send(transport);
				} catch (Exception e) {
					if (Trace.isInfoEnabled())
						Trace.info("Switcher onViewControl protocol = " + protocol, e);
				}
			}

			@Override
			public void onTunnel(Tunnel protocol) {
				try {
					protocol.send(transport);
				} catch (Exception e) {
					if (Trace.isInfoEnabled())
						Trace.info("Switcher onTunnel protocol = " + protocol, e);
				}
			}
		};
	}

	private DirectDispatcher.SwitcherReceiveable createSwitcherReceiveable(SessionObject so) {
		return new DirectDispatcher.SwitcherReceiveable() {
			@Override
			public void switcherUnicast(long sid, int ptype, Octets data) {
				SwitcherListener.getInstance().doProviderUnicast(sid, ptype, data);
			}

			@Override
			public void switcherBroadcast(int ptype, Octets data) {
				SwitcherListener.getInstance().doProviderBroadcast(ptype, data);
			}

			@Override
			public void syncViewToClients(limax.provider.providerendpoint.SyncViewToClients p) {
				try {
					SwitcherListener.getInstance().process(p);
				} catch (Exception e) {
					if (Trace.isInfoEnabled())
						Trace.info("syncViewToClients protocol = " + p, e);
				}
			}

			@Override
			public void tunnel(limax.provider.providerendpoint.Tunnel p) {
				try {
					SwitcherListener.getInstance().process(p);
				} catch (Exception e) {
					if (Trace.isInfoEnabled())
						Trace.info("tunnel protocol = " + p, e);
				}
			}
		};
	}

	final synchronized boolean checkProtocolSize(int type, int size)
			throws InstantiationException, SizePolicyException {
		Transport transport = pvidmap.get(DirectDispatcher.getProviderId(type));
		if (transport == null)
			return false;
		Integer maxSize = ((SessionObject) transport.getSessionObject()).pinfos.get(type);
		if (maxSize == null)
			throw new InstantiationException();
		if (maxSize < size)
			throw new SizePolicyException();
		return true;
	}

	final void broadcastLinkBroken(long sid, Collection<Integer> pvids) {
		Collection<Transport> transports;
		synchronized (this) {
			transports = pvids.stream().map(pvid -> pvidmap.get(pvid)).filter(Objects::nonNull).filter(transport -> {
				SessionObject so = (SessionObject) transport.getSessionObject();
				return !so.isStateless() && so.sids.remove(sid);
			}).collect(Collectors.toList());
		}
		if (transports.isEmpty())
			return;
		LinkBroken protocol = new LinkBroken(sid);
		transports.forEach(transport -> {
			try {
				protocol.send(transport);
			} catch (Exception e) {
				if (Trace.isInfoEnabled())
					Trace.info(manager + " send LinkBroken to " + transport, e);
			}
		});
	}

	synchronized int checkProvidersReady(Map<Integer, Byte> pvids) {
		for (Map.Entry<Integer, Byte> e : pvids.entrySet()) {
			ProviderArgs args = getProviderArgs(e.getKey());
			if (null == args)
				return ErrorCodes.SWITCHER_LOST_PROVIDER;
			if (Main.isSessionUseVariant(e.getValue())) {
				if (!args.isVariantSupported())
					return ErrorCodes.PROVIDER_UNSUPPORTED_VARINAT;
				else if (!args.isVariantEnabled())
					return ErrorCodes.PROVIDER_NOT_ALLOW_VARINAT;
			}
			if (Main.isSessionUseScript(e.getValue())) {
				if (!args.isScriptSupported())
					return ErrorCodes.PROVIDER_UNSUPPORTED_SCRIPT;
				else if (!args.isScriptEnabled())
					return ErrorCodes.PROVIDER_NOT_ALLOW_SCRIPT;
			}
			if (args.isStateless())
				pvids.put(e.getKey(), (byte) (e.getValue() | SessionType.ST_STATELESS));
		}
		return ErrorCodes.SUCCEED;
	}

	synchronized Collection<Long> getSessionIdsByProtocolType(int type) {
		Transport transport = pvidmap.get(DirectDispatcher.getProviderId(type));
		return transport != null ? new ArrayList<>(((SessionObject) transport.getSessionObject()).sids)
				: Collections.emptyList();
	}

	synchronized int registerClientAndGetProviderId(long sid, Transport transport) {
		SessionObject so = (SessionObject) transport.getSessionObject();
		so.sids.add(sid);
		return so.getProviderId();
	}

	static int getPvidByTransport(Transport transport) {
		return ((SessionObject) transport.getSessionObject()).pvid;
	}

	synchronized void unregisterClient(long sid, int pvid) {
		Transport transport = pvidmap.get(pvid);
		if (transport != null) {
			SessionObject so = (SessionObject) transport.getSessionObject();
			if (!so.isStateless())
				so.sids.remove(sid);
		}
	}

	synchronized boolean onlineAnnounce(Collection<Integer> pvids, Protocol protocol, Transport _transport) {
		return pvids.stream().map(pvid -> pvidmap.get(pvid)).filter(Objects::nonNull).mapToInt(transport -> {
			SessionObject so = (SessionObject) transport.getSessionObject();
			if (so.isStateless())
				return 0;
			if (so.isLoginDataSupported()) {
				try {
					if (_transport instanceof WebSocketTransport) {
						new WebSocketProtocol("$" + Integer.toString(so.pvid, Character.MAX_RADIX)) {
							@Override
							public void process() throws Exception {
							}
						}.send(_transport);
					} else {
						new ProviderLogin(new ProviderLoginData(so.pvid, ProviderLoginData.tUnused, 0, new Octets()))
								.send(_transport);
					}
				} catch (Exception e) {
					if (Trace.isInfoEnabled())
						Trace.info(
								_transport.getManager() + " request LoginData pvid = " + so.pvid + " from " + transport,
								e);
				}
			} else {
				try {
					protocol.send(transport);
				} catch (Exception e) {
					if (Trace.isInfoEnabled())
						Trace.info(manager + " send " + protocol + " to " + transport, e);
				}
			}
			return 1;
		}).sum() > 0;
	}

	synchronized void onlineAnnounce(int pvid, Protocol protocol) {
		Transport transport = pvidmap.get(pvid);
		if (transport == null)
			return;
		try {
			protocol.send(transport);
		} catch (Exception e) {
			if (Trace.isInfoEnabled())
				Trace.info(manager + " send " + protocol + " to " + transport, e);
		}
	}
}
