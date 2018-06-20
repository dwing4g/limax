package limax.endpoint;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import limax.codec.Base64Decode;
import limax.codec.CodecException;
import limax.codec.Octets;
import limax.defines.ProviderLoginData;

public final class ProviderLoginDataManager {
	private final Map<Integer, ProviderLoginData> map = new HashMap<Integer, ProviderLoginData>();

	private ProviderLoginDataManager() {
	}

	public void add(int pvid, Octets unsafedata) {
		map.put(pvid, new ProviderLoginData(pvid, ProviderLoginData.tUserData, 0, unsafedata));
	}

	public void add(int pvid, int label, Octets data) {
		map.put(pvid, new ProviderLoginData(pvid, ProviderLoginData.tTunnelData, label, data));
	}

	public void add(int pvid, int label, String data) {
		try {
			add(pvid, label, Octets.wrap(Base64Decode.transform(data.getBytes())));
		} catch (CodecException e) {
			throw new RuntimeException(e);
		}
	}

	public Collection<Integer> getProviderIds() {
		return map.keySet();
	}

	public boolean isSafe(int pvid) {
		return map.get(pvid).type == ProviderLoginData.tTunnelData;
	}

	public int getLabel(int pvid) {
		return map.get(pvid).label;
	}

	public Octets getData(int pvid) {
		return map.get(pvid).data;
	}

	public static ProviderLoginDataManager createInstance() {
		return new ProviderLoginDataManager();
	}

	ProviderLoginData get(int pvid) {
		return map.get(pvid);
	}
}
