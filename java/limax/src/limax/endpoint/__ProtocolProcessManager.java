package limax.endpoint;

import limax.endpoint.auanyviews.ServiceResult;
import limax.endpoint.providerendpoint.SyncViewToClients;
import limax.endpoint.providerendpoint.Tunnel;
import limax.endpoint.switcherendpoint.OnlineAnnounce;
import limax.endpoint.switcherendpoint.PingAndKeepAlive;
import limax.endpoint.switcherendpoint.PortForward;
import limax.endpoint.switcherendpoint.ProviderLogin;
import limax.endpoint.switcherendpoint.SHandShake;
import limax.endpoint.switcherendpoint.SessionKick;
import limax.net.Protocol;

public final class __ProtocolProcessManager {

	private __ProtocolProcessManager() {
	}

	private static EndpointManagerImpl i(Protocol p) {
		return (EndpointManagerImpl) p.getManager();
	}

	public static void process(SHandShake p) throws Exception {
		i(p).process(p);
	}

	public static void process(SessionKick p) throws Exception {
		i(p).process(p);
	}

	public static void process(PortForward p) throws Exception {
		i(p).process(p);
	}

	public static void process(ProviderLogin p) throws Exception {
		i(p).process(p);
	}

	public static void process(OnlineAnnounce p) throws Exception {
		i(p).process(p);
	}

	public static void process(PingAndKeepAlive p) throws Exception {
		i(p).process(p);
	}

	public static void process(SyncViewToClients p) throws Exception {
		i(p).process(p);
	}

	public static void process(Tunnel p) throws Exception {
		i(p).process(p);
	}

	public static void onResultViewOpen(ServiceResult view) {
		AuanyService.onResultViewOpen(view);
	}

}
