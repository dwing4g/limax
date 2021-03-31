package limax.switcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import limax.codec.Base64Decode;
import limax.codec.Base64Encode;
import limax.codec.CodecException;
import limax.codec.HmacMD5;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.codec.StringStream;
import limax.defines.ErrorCodes;
import limax.defines.ErrorSource;
import limax.defines.ProviderLoginData;
import limax.defines.SessionFlags;
import limax.defines.SessionType;
import limax.endpoint.AuanyService;
import limax.http.WebSocketAddress;
import limax.net.Config;
import limax.net.Engine;
import limax.net.Manager;
import limax.net.Protocol;
import limax.net.ServerListener;
import limax.net.ServerManager;
import limax.net.SizePolicyException;
import limax.net.StateTransport;
import limax.net.Transport;
import limax.net.UnknownProtocolHandler;
import limax.net.WebSocketProtocol;
import limax.net.WebSocketTransport;
import limax.provider.DirectDispatcher;
import limax.providerendpoint.ViewMemberData;
import limax.providerendpoint.ViewVariableData;
import limax.switcher.providerendpoint.SendControlToServer;
import limax.switcher.states.SwitcherServer;
import limax.switcher.switcherauany.Exchange;
import limax.switcher.switcherauany.SessionAuthByToken;
import limax.switcher.switcherendpoint.CHandShake;
import limax.switcher.switcherendpoint.PingAndKeepAlive;
import limax.switcher.switcherendpoint.PortForward;
import limax.switcher.switcherendpoint.ProviderLogin;
import limax.switcher.switcherendpoint.SHandShake;
import limax.switcher.switcherendpoint.SessionKick;
import limax.switcher.switcherendpoint.SessionLoginByToken;
import limax.switcher.switcherprovider.Broadcast;
import limax.switcher.switcherprovider.Kick;
import limax.switcher.switcherprovider.OnlineAnnounceAck;
import limax.switcher.switcherprovider.Unicast;
import limax.switcherauany.AuanyAuthArg;
import limax.switcherauany.AuanyAuthRes;
import limax.util.Helper;
import limax.util.Pair;
import limax.util.Trace;

public final class SwitcherListener implements ServerListener {
	private final static long handShakeTimeout = Long.getLong("limax.switcher.SwitcherListener.handShakeTimeout", 1000);
	private final static long sessionLoginTimeout = Long.getLong("limax.switcher.SwitcherListener.sessionLoginTimeout",
			20000);
	private final static long keepAliveTimeout = Long.getLong("limax.switcher.SwitcherListener.keepAliveTimeout",
			60000);
	private final static long pingProtect = Long.getLong("limax.switcher.SwitcherListener.pingProtect", 30000);
	private final static byte[] secureIp;
	private final List<ServerManager> managers = new ArrayList<>();

	static {
		String ip = System.getProperty("limax.net.secureIp");
		if (ip == null) {
			secureIp = null;
		} else {
			byte[] _secureIp;
			try {
				_secureIp = InetAddress.getByName(ip).getAddress();
			} catch (UnknownHostException e) {
				_secureIp = null;
			}
			secureIp = _secureIp;
		}
	}

	private static class SessionObject {
		final long sessionid;
		final long flags;
		private Map<Integer, Byte> _pvids;
		private volatile LmkInfo lmkInfo;
		private volatile Set<String> dictionaryKeys;
		private Map<Integer, Byte> pvids;
		private Collection<Consumer<Transport>> _cache = new ArrayList<>();
		private volatile long pingtime = 0;

		SessionObject(long sessionid, long flags, Map<Integer, Byte> pvids, LmkInfo lmkInfo,
				Set<String> dictionaryKeys) {
			this.sessionid = sessionid;
			this.flags = flags;
			this._pvids = pvids;
			this.lmkInfo = lmkInfo;
			this.dictionaryKeys = dictionaryKeys;
			this.pvids = pvids.entrySet().stream()
					.filter(e -> SessionType.ST_STATELESS == (SessionType.ST_STATELESS & e.getValue()))
					.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
		}

		@Override
		public String toString() {
			return "sessionid=" + sessionid + ", flags=" + flags + ", pvids=" + pvids;
		}

		public boolean isCanPortForward() {
			return SessionFlags.FLAG_CAN_PORT_FORWARD == (SessionFlags.FLAG_CAN_PORT_FORWARD & flags);
		}

		private boolean _isReady() {
			return null == _pvids || pvids.size() == _pvids.size();
		}

		static enum ReadyStatus {
			RS_READY_BEFORE, RS_UNREADY, RS_READY_NOW,
		}

		synchronized void cache(Consumer<Transport> r) {
			_cache.add(r);
		}

		synchronized ReadyStatus providerReady(int pvid) {
			if (_isReady())
				return pvids.containsKey(pvid) ? ReadyStatus.RS_READY_NOW : ReadyStatus.RS_READY_BEFORE;
			pvids.put(pvid, _pvids.get(pvid));
			return _isReady() ? ReadyStatus.RS_READY_NOW : ReadyStatus.RS_UNREADY;
		}

		synchronized Collection<Consumer<Transport>> ready() {
			Collection<Consumer<Transport>> result = _cache;
			_pvids = null;
			_cache = null;
			return result;
		}

		boolean isPingFlood() {
			long current = System.currentTimeMillis();
			if (current - pingtime < pingProtect)
				return true;
			pingtime = current;
			return false;
		}

		Set<String> getDictionaryKeys() {
			Set<String> tmp = dictionaryKeys;
			dictionaryKeys = null;
			return tmp;
		}

		Octets getLmkData() {
			return lmkInfo == null ? null : lmkInfo.getLmkData();
		}

		void clearLmkData() {
			if (lmkInfo == null)
				return;
			lmkInfo.getLmkData().clear();
			LmkManager.getInstance().upload(lmkInfo);
			lmkInfo = null;
		}
	}

	private static class Note {
		private final Transport transport;
		private final String platflag;
		private final Set<String> dictionaryKeys;

		Note(Transport transport, String platflag) {
			this.transport = transport;
			String[] flags = platflag.split(";");
			this.platflag = flags[0];
			this.dictionaryKeys = flags.length > 1 ? new HashSet<>(Arrays.asList(flags[1].split(",")))
					: Collections.emptySet();
		}

		Transport getTransport() {
			return transport;
		}

		String getPlatFlag() {
			return platflag;
		}

		Set<String> getDictionaryKeys() {
			return dictionaryKeys;
		}
	}

	private SwitcherListener() {
	}

	private final static SwitcherListener instance = new SwitcherListener();

	public static SwitcherListener getInstance() {
		return instance;
	}

	final UnknownProtocolHandler unknownProtocolHandler = new UnknownProtocolHandler() {
		@Override
		public void check(int type, int size, Transport transport) throws InstantiationException, SizePolicyException {
			SessionObject so = (SessionObject) transport.getSessionObject();
			int errcode = ErrorCodes.SUCCEED;
			if (so.pvids.containsKey(DirectDispatcher.getProviderId(type))) {
				if (!ProviderListener.getInstance().checkProtocolSize(type, size))
					errcode = ErrorCodes.SWITCHER_LOST_PROVIDER;
			} else
				errcode = ErrorCodes.SWITCHER_WRONG_PROVIDER;
			if (errcode != ErrorCodes.SUCCEED)
				SwitcherListener.getInstance().closeSession(transport, errcode);
		}

		@Override
		public void dispatch(int type, Octets data, Transport from) throws CodecException {
			DirectDispatcher.getInstance().getProviderDispatchableByProtocolType(type)
					.dispatchSessionProtocol(((SessionObject) from.getSessionObject()).sessionid, type, data);
			((StateTransport) from).resetAlarm(keepAliveTimeout);
		}
	};
	private final ConcurrentHashMap<Long, Transport> session2iomap = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Pair<Transport, limax.switcher.switcherprovider.OnlineAnnounce>> checkingmap = new ConcurrentHashMap<>();

	@Override
	public void onManagerInitialized(Manager manager, Config config) {
		synchronized (managers) {
			managers.add((ServerManager) manager);
		}
	}

	@Override
	public void onManagerUninitialized(Manager manager) {
		synchronized (managers) {
			managers.remove(manager);
			if (managers.size() == 0 && loginCache != null)
				loginCache.close();
		}
	}

	private boolean requestCache(SessionAuthByToken auth) {
		if (loginCache == null)
			return false;
		auth.getArgument().pvids.remove(AuanyService.providerId);
		loginCache.request(auth, a -> processResult(a), a -> _processTimeout(a));
		return true;
	}

	@Override
	public void onTransportRemoved(Transport transport)
			throws InstantiationException, SizePolicyException, CodecException {
		if (Trace.isDebugEnabled())
			Trace.debug(
					"SwitcherListener onTransportRemoved transport = " + transport + " " + transport.getCloseReason());
		Object obj = transport.getSessionObject();
		if (obj == null || !(obj instanceof SessionObject))
			return;
		SessionObject so = (SessionObject) obj;
		transport.setSessionObject(null);
		checkingmap.remove(so.sessionid);
		session2iomap.remove(so.sessionid);
		ProviderListener.getInstance().broadcastLinkBroken(so.sessionid, so.pvids.keySet());
	}

	private static byte[] packAddress(SocketAddress peeraddress, SocketAddress reportaddress) throws IOException {
		try (ByteArrayOutputStream os = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(os)) {
			oos.writeObject(peeraddress);
			oos.writeObject(reportaddress);
			return os.toByteArray();
		}
	}

	@Override
	public void onTransportAdded(Transport transport) throws Exception {
		if (Trace.isDebugEnabled())
			Trace.debug("SwitcherListener onTransportAdded transport = " + transport);
		if (transport instanceof WebSocketTransport) {
			((WebSocketTransport) transport).resetAlarm(sessionLoginTimeout);
			Note note = null;
			final WebSocketAddress wsa = (WebSocketAddress) transport.getPeerAddress();
			final AuanyAuthArg arg = new AuanyAuthArg();
			final String query = wsa.getRequestURI().getQuery();
			final String[] kvs = query.split("&");
			for (final String kv : kvs) {
				final String[] pair = kv.trim().split("=", 2);
				if (2 != pair.length)
					continue;
				final String k = pair[0].trim();
				final String v = pair[1].trim();
				switch (k) {
				case "username":
					arg.username = v;
					break;
				case "token":
					arg.token = v;
					break;
				case "platflag":
					arg.platflag = (note = new Note(transport, v)).getPlatFlag();
					break;
				case "pvids":
					for (String spvid : v.split(",")) {
						int pvid = Integer.parseInt(spvid.trim());
						if (pvid < 1 || pvid > 0xffffff)
							throw new IllegalArgumentException();
						arg.pvids.put(pvid, (byte) (SessionType.ST_SCRIPT | SessionType.ST_WEB_SOCKET));
					}
				}
			}
			arg.clientaddress.replace(packAddress(((WebSocketAddress) transport.getPeerAddress()).getSocketAddress(),
					new InetSocketAddress(0xffff)));
			arg.sockettype = AuanyAuthArg.ST_WEBSOCKET;
			if (note == null || !LmkManager.getInstance().inspect(arg, (Octets) transport.getSessionObject())) {
				closeSession(transport, ErrorCodes.AUANY_AUTHENTICATE_FAIL);
				return;
			}
			SessionAuthByToken auth = new SessionAuthByToken(arg);
			auth.setNote(note);
			Transport auanytransport = AuanyClientListener.getInstance().getTransport();
			if (auanytransport == null) {
				if (!requestCache(auth))
					closeSession(transport, ErrorCodes.SWITCHER_AUANY_UNREADY);
			} else {
				int errorcode = ProviderListener.getInstance().checkProvidersReady(arg.pvids);
				if (errorcode != ErrorCodes.SUCCEED) {
					new WebSocketProtocol(
							StringStream.create().marshal(ErrorSource.LIMAX).marshal(errorcode).toString()) {
						@Override
						public void process() throws Exception {
						}
					}.send(transport);
					close(transport);
				} else
					auth.send(auanytransport);
			}
		} else {
			((StateTransport) transport).resetAlarm(handShakeTimeout);
		}
	}

	private volatile boolean s2cneedcompress = true;
	private volatile boolean c2sneedcompress = true;
	private volatile Set<Integer> dhGroups;
	private volatile String key;
	private volatile List<Integer> nativeIds;
	private volatile List<Integer> wsIds;
	private volatile List<Integer> wssIds;
	private volatile LoginCache loginCache;

	void setNeedCompress(boolean s2c, boolean c2s) {
		s2cneedcompress = s2c;
		c2sneedcompress = c2s;
	}

	void setKey(String key) {
		this.key = key;
	}

	void setDHGroups(Set<Integer> dhGroups) {
		this.dhGroups = dhGroups;
	}

	void setNativeIds(List<Integer> ids) {
		nativeIds = ids;
	}

	void setWsIds(List<Integer> ids) {
		wsIds = ids;
	}

	void setWssIds(List<Integer> ids) {
		wssIds = ids;
	}

	void setLoginCache(String cacheGroup, int cacheCapacity) {
		try {
			if (!cacheGroup.isEmpty())
				loginCache = new LoginCache(cacheGroup, cacheCapacity);
		} catch (Exception e) {
		}
	}

	limax.switcher.switcherauany.OnlineAnnounce createOnlineAnnounce() {
		limax.switcher.switcherauany.OnlineAnnounce p = new limax.switcher.switcherauany.OnlineAnnounce();
		p.key = key;
		p.nativeIds.addAll(nativeIds);
		p.wsIds.addAll(wsIds);
		p.wssIds.addAll(wssIds);
		return p;
	}

	private void close(Transport transport) {
		transport.getManager().close(transport);
	}

	public final void process(PingAndKeepAlive p) throws Exception {
		StateTransport transport = (StateTransport) p.getTransport();
		SessionObject so = (SessionObject) transport.getSessionObject();
		if (so == null) {
			p.send(transport);
			close(transport);
		} else if (so.isPingFlood()) {
			if (Trace.isInfoEnabled())
				Trace.info("transport closed! " + transport + " isPingFlood = true");
			close(transport);
		} else {
			p.send(transport);
			transport.resetAlarm(keepAliveTimeout);
		}
	}

	public final void process(PortForward p) throws Exception {
		StateTransport transport = (StateTransport) p.getTransport();
		SessionObject so = (SessionObject) transport.getSessionObject();
		transport.resetAlarm(keepAliveTimeout);
		if (so.isCanPortForward()) {
			PortForwardManager.getInstance().onPortForward(p);
			return;
		}
		final PortForward protocol = new PortForward();
		protocol.command = PortForward.eClose;
		protocol.portsid = p.portsid;
		protocol.code = PortForward.eCloseNoAuthority;
		protocol.send(transport);
	}

	public final void process(CHandShake protocol) throws Exception {
		StateTransport transport = (StateTransport) protocol.getTransport();
		int group = protocol.dh_group;
		if (!dhGroups.contains(group)) {
			closeSession(transport, ErrorCodes.SWITCHER_DHGROUP_NOTSUPPRTED);
			return;
		}
		transport.resetAlarm(sessionLoginTimeout);
		BigInteger data = new BigInteger(protocol.dh_data.getBytes());
		BigInteger rand = Helper.makeDHRandom();
		byte[] material = Helper.computeDHKey(group, data, rand).toByteArray();
		byte[] key = secureIp != null ? secureIp
				: ((InetSocketAddress) transport.getLocalAddress()).getAddress().getAddress();
		int half = material.length / 2;
		HmacMD5 mac = new HmacMD5(key, 0, key.length);
		mac.update(material, 0, half);
		transport.setInputSecurityCodec(mac.digest(), c2sneedcompress);
		new SHandShake(Octets.wrap(Helper.generateDHResponse(group, rand).toByteArray()), c2sneedcompress,
				s2cneedcompress).send(transport);
		mac = new HmacMD5(key, 0, key.length);
		mac.update(material, half, material.length - half);
		transport.setOutputSecurityCodec(mac.digest(), s2cneedcompress);
		transport.setState(SwitcherServer.EndpointSessionLogin);
		transport.setSessionObject(new Octets(material));
	}

	public final void process(SessionLoginByToken p) throws Exception {
		Transport transport = p.getTransport();
		Note note = new Note(transport, p.platflag);
		AuanyAuthArg arg = new AuanyAuthArg(p.username, p.token, note.getPlatFlag(), p.pvids,
				new Octets(packAddress(transport.getPeerAddress(), new InetSocketAddress(
						InetAddress.getByAddress(p.report_ip.getBytes()), p.report_port & 0xffff))),
				(byte) AuanyAuthArg.ST_SOCKET);
		if (!LmkManager.getInstance().inspect(arg, (Octets) transport.getSessionObject())) {
			closeSession(transport, ErrorCodes.AUANY_AUTHENTICATE_FAIL);
			return;
		}
		SessionAuthByToken auth = new SessionAuthByToken(arg);
		auth.setNote(note);
		Transport auanytransport = AuanyClientListener.getInstance().getTransport();
		if (auanytransport == null) {
			if (!requestCache(auth))
				closeSession(transport, ErrorCodes.SWITCHER_AUANY_UNREADY);
		} else {
			int errorcode = ProviderListener.getInstance().checkProvidersReady(p.pvids);
			if (errorcode != ErrorCodes.SUCCEED)
				closeSession(transport, errorcode);
			else
				auth.send(auanytransport);
		}
	}

	private void closeDuplicateCheckingTransport(Transport transport) {
		try {
			limax.switcher.switcherendpoint.OnlineAnnounce p = new limax.switcher.switcherendpoint.OnlineAnnounce();
			p.errorSource = ErrorSource.LIMAX;
			p.errorCode = ErrorCodes.PROVIDER_SESSION_LOGINED;
			p.send(transport);
		} catch (Exception e) {
		}
		close(transport);
	}

	public final void process(ProviderLogin p) {
		StateTransport transport = (StateTransport) p.getTransport();
		SessionObject so = (SessionObject) transport.getSessionObject();
		if (so == null) {
			close(transport);
			return;
		}
		process(p.data, checkingmap.get(so.sessionid));
	}

	private void process(ProviderLoginData data, Pair<Transport, limax.switcher.switcherprovider.OnlineAnnounce> pair) {
		limax.switcher.switcherprovider.OnlineAnnounce template = pair.getValue();
		limax.switcher.switcherprovider.OnlineAnnounce protocol = new limax.switcher.switcherprovider.OnlineAnnounce();
		protocol.sessionid = template.sessionid;
		protocol.mainid = template.mainid;
		protocol.uid = template.uid;
		protocol.clientaddress = template.clientaddress;
		protocol.flags = template.flags;
		protocol.sessiontype = template.sessiontype;
		protocol.logindata = data;
		ProviderListener.getInstance().onlineAnnounce(data.pvid, protocol);
	}

	public final void processResult(SessionAuthByToken auth) {
		final AuanyAuthArg arg = auth.getArgument();
		final AuanyAuthRes res = auth.getResult();
		final Note note = (Note) auth.getNote();
		final Transport transport = note.getTransport();
		final boolean loginsucceed = ErrorSource.LIMAX == res.errorSource && ErrorCodes.SUCCEED == res.errorCode;
		try {
			if (loginsucceed) {
				if (loginCache != null && AuanyClientListener.getInstance().getTransport() != null)
					loginCache.update(auth);
				final SessionObject so = new SessionObject(res.sessionid, res.flags, arg.pvids,
						res.lmkdata.size() == 0 ? null : new LmkInfo(res.uid, res.lmkdata), note.getDictionaryKeys());
				limax.switcher.switcherprovider.OnlineAnnounce protocol = new limax.switcher.switcherprovider.OnlineAnnounce();
				protocol.sessionid = res.sessionid;
				protocol.mainid = res.mainid;
				protocol.uid = res.uid;
				protocol.clientaddress.swap(arg.clientaddress);
				protocol.flags = res.flags;
				protocol.sessiontype.putAll(arg.pvids);
				protocol.logindata.type = ProviderLoginData.tUnused;
				Pair<Transport, limax.switcher.switcherprovider.OnlineAnnounce> pair = checkingmap.put(so.sessionid,
						new Pair<>(transport, protocol));
				if (pair != null)
					closeDuplicateCheckingTransport(pair.getKey());
				boolean hasStatefulProvider = ProviderListener.getInstance().onlineAnnounce(arg.pvids.keySet(),
						protocol, transport);
				transport.setSessionObject(so);
				if (transport instanceof StateTransport) {
					((StateTransport) transport).setState(SwitcherServer.EndpointClient);
					((StateTransport) transport).resetAlarm(keepAliveTimeout);
				} else {
					((WebSocketTransport) transport).resetAlarm(keepAliveTimeout);
				}
				if (!hasStatefulProvider)
					processOnline(so.sessionid, ErrorCodes.SUCCEED, auth.getTransport());
			} else {
				if (transport instanceof StateTransport) {
					limax.switcher.switcherendpoint.OnlineAnnounce protocol = new limax.switcher.switcherendpoint.OnlineAnnounce();
					protocol.errorSource = res.errorSource;
					protocol.errorCode = res.errorCode;
					protocol.send(transport);
				} else {
					new WebSocketProtocol(
							StringStream.create().marshal(res.errorSource).marshal(res.errorCode).toString()) {
						@Override
						public void process() throws Exception {
						}
					}.send(transport);
				}
				close(transport);
			}
		} catch (Exception e) {
			if (Trace.isInfoEnabled())
				Trace.info("Transport " + transport + " closed. SwitcherListener.processSessionAuthByTokenResult", e);
			close(transport);
		}
	}

	public final void processTimeout(SessionAuthByToken auth) {
		if (!requestCache(auth))
			_processTimeout(auth);
	}

	private void _processTimeout(SessionAuthByToken auth) {
		final Transport transport = ((Note) auth.getNote()).getTransport();
		try {
			if (transport instanceof StateTransport) {
				limax.switcher.switcherendpoint.OnlineAnnounce p = new limax.switcher.switcherendpoint.OnlineAnnounce();
				p.errorSource = ErrorSource.LIMAX;
				p.errorCode = ErrorCodes.SWITCHER_AUANY_TIMEOUT;
				p.send(transport);
			} else {
				new WebSocketProtocol(StringStream.create().marshal(ErrorSource.LIMAX)
						.marshal(ErrorCodes.SWITCHER_AUANY_TIMEOUT).toString()) {
					@Override
					public void process() throws Exception {
					}
				}.send(transport);
			}
		} catch (Exception e) {
			if (Trace.isInfoEnabled())
				Trace.info("SwitcherListener.processSessionAuthByTokenTimeout", e);
		} finally {
			close(transport);
		}
	}

	private void hashSchedule(long sessionid, Runnable r) {
		Engine.getProtocolExecutor().execute(sessionid, r);
	}

	public final void process(OnlineAnnounceAck p) {
		hashSchedule(p.sessionid, () -> processOnline(p.sessionid, p.error, p.getTransport()));
	}

	private void processOnline(long sessionid, int error, Transport _transport) {
		Pair<Transport, limax.switcher.switcherprovider.OnlineAnnounce> pair = checkingmap.get(sessionid);
		if (pair == null)
			return;
		final Transport transport = pair.getKey();
		final SessionObject so = (SessionObject) transport.getSessionObject();
		if (so == null)
			return;
		if (error == ErrorCodes.SUCCEED) {
			switch (so.providerReady(
					ProviderListener.getInstance().registerClientAndGetProviderId(sessionid, _transport))) {
			case RS_READY_BEFORE:
				if (Trace.isErrorEnabled())
					Trace.error("session is ready before recv OnlineAnnounce from provider!");
				return;
			case RS_UNREADY:
				return;
			case RS_READY_NOW:
				break;
			}
			checkingmap.remove(sessionid);
			Transport oldtransport = session2iomap.put(sessionid, transport);
			if (null != oldtransport && Trace.isErrorEnabled())
				Trace.error("ERROR : duplicate transport [" + transport + "] [" + oldtransport + "]");
			try {
				if (transport instanceof WebSocketTransport) {
					StringStream sstream = StringStream.create().marshal(ErrorSource.LIMAX).marshal(ErrorCodes.SUCCEED)
							.marshal(sessionid).marshal(so.flags).append("L");
					Set<String> dictionaryKeys = so.getDictionaryKeys();
					so.pvids.keySet().forEach(pvid -> {
						ProviderArgs provider = ProviderListener.getInstance().getProviderArgs(pvid);
						sstream.marshal(pvid);
						if (dictionaryKeys.contains(provider.getScriptDefinesKey()))
							sstream.marshal(provider.getScriptDefinesKey()).append("U");
						else
							sstream.append(provider.getScriptDefines());
					});
					sstream.append(':');
					Octets lmkdata = so.getLmkData();
					sstream.marshal(lmkdata != null ? new String(Base64Encode.transform(lmkdata.getBytes())) : "");
					new WebSocketProtocol(sstream.toString()) {
						@Override
						public void process() throws Exception {
						}
					}.send(transport);
				} else {
					limax.switcher.switcherendpoint.OnlineAnnounce p = new limax.switcher.switcherendpoint.OnlineAnnounce();
					Set<Integer> scriptpvids = new HashSet<>();
					so.pvids.forEach((k, v) -> {
						ProviderArgs a = ProviderListener.getInstance().getProviderArgs(k);
						if (Main.isSessionUseVariant(v))
							p.variantdefines.put(a.getProviderId(), a.getVariantDefines());
						if (Main.isSessionUseScript(v))
							scriptpvids.add(a.getProviderId());
					});
					if (!scriptpvids.isEmpty()) {
						StringStream sstream = StringStream.create().marshal(ErrorSource.LIMAX)
								.marshal(ErrorCodes.SUCCEED).marshal(sessionid).marshal(so.flags).append("L");
						Set<String> dictionaryKeys = so.getDictionaryKeys();
						scriptpvids.forEach(pvid -> {
							ProviderArgs provider = ProviderListener.getInstance().getProviderArgs(pvid);
							sstream.marshal(pvid);
							if (dictionaryKeys.contains(provider.getScriptDefinesKey()))
								sstream.marshal(provider.getScriptDefinesKey()).append("U");
							else
								sstream.append(provider.getScriptDefines());
						});
						p.scriptdefines = sstream.append(":").marshal("").toString();
					}
					p.sessionid = sessionid;
					p.flags = so.flags;
					p.errorSource = ErrorSource.LIMAX;
					p.errorCode = error;
					Octets lmkdata = so.getLmkData();
					if (lmkdata != null)
						p.lmkdata = lmkdata;
					p.send(transport);
				}
				so.ready().forEach(c -> c.accept(transport));
			} catch (Exception e) {
				if (Trace.isInfoEnabled())
					Trace.info("switcher send protocol exception", e);
				close(transport);
			}
		} else {
			ProviderListener.getInstance().broadcastLinkBroken(so.sessionid, so.pvids.keySet());
			transport.setSessionObject(null);
			try {
				if (transport instanceof WebSocketTransport) {
					new WebSocketProtocol(
							StringStream.create().marshal(ErrorSource.LIMAX).marshal(error).append("L").toString(":")) {
						@Override
						public void process() throws Exception {
						}
					}.send(transport);
				} else {
					limax.switcher.switcherendpoint.OnlineAnnounce p = new limax.switcher.switcherendpoint.OnlineAnnounce();
					p.errorSource = ErrorSource.LIMAX;
					p.errorCode = error;
					p.send(transport);
				}
				close(transport);
			} catch (Exception e) {
				if (Trace.isInfoEnabled())
					Trace.info("switcher send protocol exception", e);
				close(transport);
			}
		}
	}

	public final void process(SendControlToServer p) {
		int providerid;
		short classindex;
		int instanceindex;
		byte controlindex;
		String message;
		StateTransport transport = (StateTransport) p.getTransport();
		if (p.providerid == -1) {
			String s = p.stringdata;
			providerid = Integer.parseInt(s.substring(0, s.indexOf(',')), Character.MAX_RADIX);
			int p0 = s.indexOf(',') + 1;
			int p1 = s.indexOf(',', p0);
			int classindexOrLabel = Integer.parseInt(s.substring(p0, p1), Character.MAX_RADIX);
			int p2 = s.indexOf(':', ++p1);
			if (p2 == -1) {
				try {
					process(new limax.switcher.providerendpoint.Tunnel(providerid, 0, classindexOrLabel,
							Octets.wrap(Base64Decode.transform(s.substring(p1).getBytes()))), transport);
				} catch (CodecException e) {
					closeSession(transport, ErrorCodes.SWITCHER_MALFORMED_TUNNELDATA);
				}
				return;
			}
			classindex = (short) classindexOrLabel;
			instanceindex = Integer.parseInt(s.substring(p1, p2), Character.MAX_RADIX);
			controlindex = -1;
			message = s.substring(++p2);
		} else {
			providerid = p.providerid;
			classindex = p.classindex;
			instanceindex = p.instanceindex;
			controlindex = p.controlindex;
			message = p.stringdata;
		}
		SessionObject so = (SessionObject) transport.getSessionObject();
		if (!so.pvids.containsKey(providerid)) {
			if (providerid != AuanyService.providerId || AuanyClientListener.getInstance().getTransport() != null)
				closeSession(transport, ErrorCodes.SWITCHER_WRONG_PROVIDER);
			return;
		}
		DirectDispatcher.ProviderDispatchable dispatchable = DirectDispatcher.getInstance()
				.getProviderDispatchable(providerid);
		if (dispatchable != null) {
			dispatchable.onViewControl(new limax.provider.providerendpoint.SendControlToServer(providerid, so.sessionid,
					classindex, instanceindex, controlindex, p.controlparameter, message));
			transport.resetAlarm(keepAliveTimeout);
		} else {
			closeSession(transport, ErrorCodes.SWITCHER_PROVIDER_UNBIND);
		}
	}

	public void process(WebSocketProtocol webSocketProtocol) throws Exception {
		WebSocketTransport transport = webSocketProtocol.getTransport();
		SessionObject so = (SessionObject) transport.getSessionObject();
		String s = webSocketProtocol.getText();
		Pair<Transport, limax.switcher.switcherprovider.OnlineAnnounce> pair = checkingmap.get(so.sessionid);
		if (pair != null) {
			ProviderLoginData data = new ProviderLoginData();
			data.pvid = Integer.parseInt(s.substring(0, s.indexOf(',')), Character.MAX_RADIX);
			int p0 = s.indexOf(',') + 1;
			int p1 = s.indexOf(',', p0);
			data.type = Integer.parseInt(p1 < 0 ? s.substring(p0) : s.substring(p0, p1), Character.MAX_RADIX);
			switch (data.type) {
			case ProviderLoginData.tUnused:
				break;
			case ProviderLoginData.tTunnelData:
				p0 = s.indexOf(',', ++p1);
				data.label = Integer.parseInt(s.substring(p1, p0), Character.MAX_RADIX);
				data.data = Octets.wrap(Base64Decode.transform(s.substring(p0 + 1).getBytes()));
				break;
			case ProviderLoginData.tUserData:
				data.data = Octets.wrap(s.substring(p1 + 1).getBytes());
				break;
			default:
				data.type = ProviderLoginData.tUserData;
				data.data = Octets.wrap(Base64Decode.transform(s.substring(p1 + 1).getBytes()));
			}
			process(data, pair);
		} else {
			if (s.equals(" ")) {
				if (so == null || so.isPingFlood())
					close(transport);
				else
					transport.resetAlarm(keepAliveTimeout);
			} else {
				int providerid = Integer.parseInt(s.substring(0, s.indexOf(',')), Character.MAX_RADIX);
				if (providerid == AuanyService.providerId) {
					so.clearLmkData();
					return;
				}
				DirectDispatcher.ProviderDispatchable dispatchable = DirectDispatcher.getInstance()
						.getProviderDispatchable(providerid);
				if (dispatchable != null) {
					dispatchable.dispatchSessionProtocol(
							((SessionObject) webSocketProtocol.getTransport().getSessionObject()).sessionid, -1,
							new OctetsStream().marshal(webSocketProtocol));
					transport.resetAlarm(keepAliveTimeout);
				} else {
					closeSession(transport, ErrorCodes.SWITCHER_PROVIDER_UNBIND);
				}
			}
		}
	}

	private static class ForwardSyncViewToClients {
		private final Data data;
		private final int pvid;
		private Protocol protocol = null;
		private Protocol script = null;

		private ForwardSyncViewToClients(Data data, int pvid) {
			this.data = data;
			this.pvid = pvid;
		}

		public Protocol getProtocol() {
			if (null == protocol)
				protocol = data.createProtocol();
			return protocol;
		}

		public Protocol getScript() {
			if (null == script)
				script = data.createScript();
			return script;
		}

		public Protocol getFull() {
			return data.getFull();
		}

		public String getScriptString() {
			return data.getScriptString();
		}

		public int getProviderId() {
			return pvid;
		}

		private interface Data {

			Protocol createProtocol();

			Protocol createScript();

			Protocol getFull();

			String getScriptString();
		}

		public static ForwardSyncViewToClients create(final limax.switcher.providerendpoint.SyncViewToClients p) {
			return new ForwardSyncViewToClients(new Data() {

				@Override
				public Protocol createProtocol() {
					return new limax.switcher.providerendpoint.SyncViewToClients(p.providerid, p.sessionids,
							p.classindex, p.instanceindex, p.synctype, p.vardatas, p.members, "");
				}

				@Override
				public Protocol createScript() {
					return new limax.switcher.providerendpoint.SyncViewToClients(p.providerid, p.sessionids,
							p.classindex, p.instanceindex, p.synctype, new ArrayList<ViewVariableData>(),
							new ArrayList<ViewMemberData>(), p.stringdata);
				}

				@Override
				public Protocol getFull() {
					return p;
				}

				@Override
				public String getScriptString() {
					return p.stringdata;
				}
			}, p.providerid);
		}

		public static ForwardSyncViewToClients create(final limax.provider.providerendpoint.SyncViewToClients p) {
			return new ForwardSyncViewToClients(new Data() {

				@Override
				public Protocol createProtocol() {
					return new limax.switcher.providerendpoint.SyncViewToClients(p.providerid, p.sessionids,
							p.classindex, p.instanceindex, p.synctype, p.vardatas, p.members, "");
				}

				@Override
				public Protocol createScript() {
					return new limax.switcher.providerendpoint.SyncViewToClients(p.providerid, p.sessionids,
							p.classindex, p.instanceindex, p.synctype, new ArrayList<ViewVariableData>(),
							new ArrayList<ViewMemberData>(), p.stringdata);
				}

				@Override
				public Protocol getFull() {
					return p;
				}

				@Override
				public String getScriptString() {
					return p.stringdata;
				}
			}, p.providerid);
		}
	}

	private void _forwardSyncViewToClients(Transport transport, ForwardSyncViewToClients fscv) {
		try {
			if (transport instanceof WebSocketTransport) {
				new WebSocketProtocol(fscv.getScriptString()) {
					@Override
					public void process() throws Exception {
					}
				}.send(transport);
			} else {
				SessionObject so = (SessionObject) transport.getSessionObject();
				if (so == null)
					return;
				byte st = so.pvids.get(fscv.getProviderId());
				if (Main.isSessionUseViewProtocol(st) && Main.isSessionUseScript(st))
					fscv.getFull().send(transport);
				else if (Main.isSessionUseScript(st))
					fscv.getScript().send(transport);
				else
					fscv.getProtocol().send(transport);
			}
		} catch (Exception e) {
			if (Trace.isInfoEnabled())
				Trace.info(transport + " SwitcherListener.forwardSyncViewToClients", e);
			closeSession(transport, ErrorCodes.SWITCHER_SEND_TO_ENDPOINT_EXCEPTION);
		}
	}

	private void forwardSyncViewToClients(Collection<Long> sessionids, ForwardSyncViewToClients fscv) {
		for (Long sessionid : sessionids)
			hashSchedule(sessionid, () -> {
				Transport transport = session2iomap.get(sessionid);
				if (transport != null)
					_forwardSyncViewToClients(transport, fscv);
				else {
					Pair<Transport, limax.switcher.switcherprovider.OnlineAnnounce> pair = checkingmap.get(sessionid);
					if (pair != null) {
						SessionObject so = (SessionObject) pair.getKey().getSessionObject();
						if (so != null)
							so.cache(_transport -> _forwardSyncViewToClients(_transport, fscv));
					}
				}
			});
	}

	private final static ArrayList<Long> nullList = new ArrayList<>();

	public void process(limax.provider.providerendpoint.SyncViewToClients p) {
		final Collection<Long> sessionids = p.sessionids;
		p.sessionids = nullList;
		forwardSyncViewToClients(sessionids, ForwardSyncViewToClients.create(p));
	}

	public void process(limax.switcher.providerendpoint.SyncViewToClients p) {
		final Collection<Long> sessionids = p.sessionids;
		p.sessionids = nullList;
		forwardSyncViewToClients(sessionids, ForwardSyncViewToClients.create(p));
	}

	private void _forwardTunnel(Transport transport, int providerid, int label, Octets data) {
		try {
			if (transport instanceof WebSocketTransport) {
				new WebSocketProtocol(StringStream.create().marshal(new String(Base64Encode.transform(data.getBytes())))
						.marshal(providerid).marshal(label).toString()) {
					@Override
					public void process() throws Exception {
					}
				}.send(transport);
			} else {
				new limax.switcher.providerendpoint.Tunnel(providerid, 0, label, data).send(transport);
			}
		} catch (Exception e) {
			if (Trace.isInfoEnabled())
				Trace.info(transport + " SwitcherListener.forwardTunnel", e);
			closeSession(transport, ErrorCodes.SWITCHER_SEND_TO_ENDPOINT_EXCEPTION);
		}
	}

	private void forwardTunnel(long sessionid, int providerid, int label, Octets data) {
		hashSchedule(sessionid, () -> {
			Transport transport = session2iomap.get(sessionid);
			if (transport != null)
				_forwardTunnel(transport, providerid, label, data);
			else {
				Pair<Transport, limax.switcher.switcherprovider.OnlineAnnounce> pair = checkingmap.get(sessionid);
				if (pair != null) {
					SessionObject so = (SessionObject) pair.getKey().getSessionObject();
					if (so != null)
						so.cache(_transport -> _forwardTunnel(_transport, providerid, label, data));
				}
			}
		});
	}

	public void process(limax.provider.providerendpoint.Tunnel p) {
		forwardTunnel(p.sessionid, p.providerid, p.label, p.data);
	}

	public void process(limax.switcher.providerendpoint.Tunnel p) {
		process(p, (StateTransport) p.getTransport());
	}

	private void process(limax.switcher.providerendpoint.Tunnel p, StateTransport transport) {
		int providerid = p.providerid;
		Object sessionObject = transport.getSessionObject();
		if (sessionObject instanceof SessionObject) {
			SessionObject so = (SessionObject) sessionObject;
			if (providerid == AuanyService.providerId) {
				so.clearLmkData();
				return;
			}
			DirectDispatcher.ProviderDispatchable dispatchable = DirectDispatcher.getInstance()
					.getProviderDispatchable(providerid);
			if (dispatchable != null) {
				dispatchable.onTunnel(
						new limax.provider.providerendpoint.Tunnel(providerid, so.sessionid, p.label, p.data));
				transport.resetAlarm(keepAliveTimeout);
			} else {
				closeSession(transport, ErrorCodes.SWITCHER_PROVIDER_UNBIND);
			}
		} else {
			forwardTunnel(p.sessionid, p.providerid, p.label, p.data);
		}
	}

	void kickSession(int pvid, long sessionid, int error) {
		hashSchedule(sessionid, () -> {
			Transport transport = session2iomap.remove(sessionid);
			if (transport == null)
				return;
			SessionObject so = (SessionObject) transport.getSessionObject();
			if (so == null)
				return;
			transport.setSessionObject(null);
			if (error != ErrorCodes.PROVIDER_DUPLICATE_SESSION) {
				ProviderListener.getInstance().unregisterClient(sessionid, pvid);
				so.pvids.remove(pvid);
				if (!so.pvids.isEmpty())
					ProviderListener.getInstance().broadcastLinkBroken(so.sessionid, so.pvids.keySet());
			}
			closeSession(transport, error);
		});
	}

	void closeSession(long sessionid, int error) {
		hashSchedule(sessionid, () -> {
			Transport transport = session2iomap.get(sessionid);
			if (transport != null)
				closeSession(transport, error);
		});
	}

	void closeSession(Transport transport, int error) {
		if (Trace.isInfoEnabled())
			Trace.info("switcher kick session " + transport + " " + error);
		try {
			if (transport instanceof WebSocketTransport)
				new WebSocketProtocol("" + error) {
					@Override
					public void process() throws Exception {
					}
				}.send(transport);
			else
				new SessionKick(error).send(transport);
		} catch (Exception e) {
			if (Trace.isInfoEnabled())
				Trace.info("SwitcherListener.kickSession send SessionKick", e);
		} finally {
			close(transport);
		}
	}

	private void _doProviderUnicast(Transport transport, int ptype, Octets data) {
		try {
			unknownProtocolHandler.send(ptype, data, transport);
		} catch (Exception e) {
			if (Trace.isInfoEnabled())
				Trace.info(transport + " closed. SwitcherListener.doProviderSend", e);
			closeSession(transport, ErrorCodes.SWITCHER_SEND_TO_ENDPOINT_EXCEPTION);
		}
	}

	void doProviderUnicast(long sessionid, int ptype, Octets data) {
		hashSchedule(sessionid, () -> {
			Transport transport = session2iomap.get(sessionid);
			if (transport != null)
				_doProviderUnicast(transport, ptype, data);
			else {
				Pair<Transport, limax.switcher.switcherprovider.OnlineAnnounce> pair = checkingmap.get(sessionid);
				if (pair != null) {
					SessionObject so = (SessionObject) pair.getKey().getSessionObject();
					if (so != null)
						so.cache(_transport -> _doProviderUnicast(_transport, ptype, data));
				}
			}
		});
	}

	void doProviderBroadcast(int ptype, Octets data) {
		ProviderListener.getInstance().getSessionIdsByProtocolType(ptype)
				.forEach(sessionid -> doProviderUnicast(sessionid, ptype, data));
	}

	public void process(Kick p) {
		int pvid = ProviderListener.getPvidByTransport(p.getTransport());
		kickSession(pvid, p.sessionid, p.error);
	}

	public void process(Unicast p) {
		doProviderUnicast(p.sessionid, p.ptype, p.pdata);
	}

	public void process(Broadcast p) {
		doProviderBroadcast(p.ptype, p.pdata);
	}

	public void process(Exchange exchange) throws Exception {
		switch (exchange.type) {
		case Exchange.CONFIG_LMKMASQUERADE:
			LmkManager.getInstance().setup(new LmkConfigData(exchange.data)
					.createLmkMasquerade(lmkInfo -> LmkManager.getInstance().upload(lmkInfo)));
			return;
		case Exchange.UPLOAD_LMKDATA:
			LmkManager.getInstance().ack(new LmkInfo(exchange.data).getUid());
		}
	}
}
