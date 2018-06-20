package limax.provider;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.SocketAddress;
import java.util.concurrent.Future;

import limax.codec.CodecException;
import limax.codec.Octets;
import limax.defines.SessionType;
import limax.net.Manager;
import limax.net.SizePolicyException;
import limax.net.SupportStateCheck;
import limax.net.SupportTypedDataTransfer;
import limax.net.Transport;
import limax.provider.switcherprovider.OnlineAnnounce;

class ProviderTransportImpl implements ProviderTransport, SupportStateCheck, SupportTypedDataTransfer {
	private volatile Object sessionobj = null;
	private final ProviderManagerImpl provider;
	private final Transport relativeTransport;
	private final ViewSession viewsession = new ViewSession();
	private final long sessionid;
	private final long mainid;
	private final String uid;
	private LoginData logindata;
	private final SocketAddress peeraddress;
	private final SocketAddress reportaddress;
	private final long accountflags;
	private final int sessionType;
	private volatile Future<?> future = null;

	ProviderTransportImpl(ProviderManagerImpl provider, OnlineAnnounce oa) {
		this.provider = provider;
		this.relativeTransport = oa.getTransport();
		this.sessionid = oa.sessionid;
		this.mainid = oa.mainid;
		this.uid = oa.uid;
		this.accountflags = oa.flags;
		SocketAddress peeraddress;
		SocketAddress reportaddress;
		try (final ObjectInputStream ois = new ObjectInputStream(
				new ByteArrayInputStream(oa.clientaddress.array(), 0, oa.clientaddress.size()))) {
			peeraddress = (SocketAddress) ois.readObject();
			reportaddress = (SocketAddress) ois.readObject();
		} catch (Exception e) {
			peeraddress = reportaddress = new SocketAddress() {
				private static final long serialVersionUID = -210184168558204443L;
			};
		}
		this.peeraddress = peeraddress;
		this.reportaddress = reportaddress;
		this.sessionType = oa.sessiontype.get(provider.getPvid());
	}

	ProviderTransportImpl(ProviderManagerImpl provider, long sessionid, Transport from) {
		this.provider = provider;
		this.relativeTransport = from;
		this.sessionid = sessionid;
		this.mainid = 0;
		this.uid = "";
		this.accountflags = 0;
		this.peeraddress = this.reportaddress = new SocketAddress() {
			private static final long serialVersionUID = -210184168558204443L;
		};
		this.sessionType = SessionType.ST_STATELESS;
	}

	Transport getRelativeTransport() {
		return relativeTransport;
	}

	@Override
	public long getSessionId() {
		return sessionid;
	}

	@Override
	public long getMainId() {
		return mainid;
	}

	@Override
	public String getUid() {
		return uid;
	}

	@Override
	public LoginData getLoginData() {
		return logindata;
	}

	void setLoginData(LoginData logindata) {
		this.logindata = logindata;
	}

	@Override
	public String toString() {
		return getClass().getName() + " (" + getLocalAddress() + " - " + getPeerAddress() + ")(" + mainid + "/" + uid
				+ ")[" + sessionobj + "]";
	}

	@Override
	public long getAccountFlags() {
		return accountflags;
	}

	@Override
	public SocketAddress getPeerAddress() {
		return peeraddress;
	}

	@Override
	public SocketAddress getLocalAddress() {
		return new ProviderSocketAddress(sessionid);
	}

	@Override
	public Object getSessionObject() {
		return sessionobj;
	}

	@Override
	public void setSessionObject(Object obj) {
		sessionobj = obj;
	}

	@Override
	public Manager getManager() {
		return provider.getOutmostWrapperManager();
	}

	@Override
	public Throwable getCloseReason() {
		return null;
	}

	@Override
	public void check(int type, int size) throws InstantiationException, SizePolicyException {
		provider.check(type, size);
	}

	@Override
	public void send(int type, Octets data) throws CodecException {
		try {
			provider.send(sessionid, type, data);
		} catch (CodecException e) {
			throw e;
		} catch (Exception e) {
			throw new CodecException(e);
		}
	}

	ViewSession getViewSession() {
		return viewsession;
	}

	@Override
	public boolean isUseVariant() {
		return SessionType.ST_VARIANT == (SessionType.ST_VARIANT & sessionType);
	}

	@Override
	public boolean isUseScript() {
		return SessionType.ST_SCRIPT == (SessionType.ST_SCRIPT & sessionType);
	}

	@Override
	public boolean isUseStatic() {
		return SessionType.ST_STATIC == (SessionType.ST_STATIC & sessionType);
	}

	@Override
	public boolean isWebSocket() {
		return SessionType.ST_WEB_SOCKET == (SessionType.ST_WEB_SOCKET & sessionType);
	}

	@Override
	public boolean isStateless() {
		return SessionType.ST_STATELESS == (SessionType.ST_STATELESS & sessionType);
	}

	@Override
	public SocketAddress getReportAddress() {
		return reportaddress;
	}

	void updateFuture(Future<?> future) {
		if (this.future != null)
			this.future.cancel(false);
		this.future = future;
	}
}
