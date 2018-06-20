package limax.provider;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import limax.codec.Octets;
import limax.provider.providerendpoint.SendControlToServer;
import limax.provider.providerendpoint.SyncViewToClients;
import limax.provider.providerendpoint.Tunnel;

public class DirectDispatcher {

	public interface SwitcherReceiveable {
		void switcherUnicast(long sid, int ptype, Octets data);

		void switcherBroadcast(int ptype, Octets data);

		void syncViewToClients(SyncViewToClients protocol);
		
		void tunnel(Tunnel protocol);
	}

	public interface ProviderDispatchable {
		void dispatchSessionProtocol(long sid, int ptype, Octets pdata);

		void onViewControl(SendControlToServer protocol);

		void onTunnel(Tunnel protocol);

		void setSwitcherReceiveable(SwitcherReceiveable recvable);
	}

	private final static DirectDispatcher instance = new DirectDispatcher();

	private DirectDispatcher() {
	}

	public static DirectDispatcher getInstance() {
		return instance;
	}

	public static int getProviderId(int ptype) {
		return ptype >>> 8;
	}

	private final Map<Integer, ProviderDispatchable> dispatchmap = Collections
			.synchronizedMap(new HashMap<Integer, ProviderDispatchable>());

	public boolean register(int pvid, ProviderDispatchable dispatch) {
		return null != dispatchmap.put(pvid, dispatch);
	}

	public void unregister(int pvid) {
		dispatchmap.remove(pvid);
	}

	public ProviderDispatchable getProviderDispatchableByProtocolType(int type) {
		return getProviderDispatchable(getProviderId(type));
	}

	public ProviderDispatchable getProviderDispatchable(int pvid) {
		return dispatchmap.get(pvid);
	}
}
