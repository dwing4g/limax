package limax.provider;

import limax.net.Protocol;
import limax.provider.providerendpoint.SendControlToServer;
import limax.provider.providerendpoint.Tunnel;
import limax.provider.switcherprovider.BindResult;
import limax.provider.switcherprovider.LinkBroken;
import limax.provider.switcherprovider.OnlineAnnounce;
import limax.provider.switcherprovider.Pay;
import limax.provider.switcherprovider.PayAck;

public final class __ProtocolProcessManager {
	private __ProtocolProcessManager() {
	}

	private static ProviderManagerImpl i(Protocol p) {
		return ((ProviderSwitcherExchanger.SessionObject) p.getTransport().getSessionObject()).getProviderManager();
	}

	public static void process(LinkBroken p) {
		i(p).process(p);
	}

	public static void process(OnlineAnnounce p) {
		i(p).process(p);
	}

	public static void process(SendControlToServer p) {
		i(p).process(p);
	}

	public static void process(Tunnel p) {
		i(p).process(p);
	}

	public static void process(BindResult p) {
		i(p).process(p);
	}

	public static void process(Pay p) {
		i(p).process(p);
	}

	public static void process(PayAck p) {
		i(p).process(p);
	}
}
