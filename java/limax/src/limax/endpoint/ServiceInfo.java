package limax.endpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import limax.codec.JSON;

public final class ServiceInfo {
	private final static Random random = new Random(System.currentTimeMillis());
	private final List<SwitcherConfig> switchers = new ArrayList<SwitcherConfig>();
	final int appid;
	private final int[] pvids;
	private final int[] payids;
	private final JSON[] userjsons;
	private final boolean running;
	private final String optional;

	static class SwitcherConfig {
		final String host;
		final int port;

		SwitcherConfig(String host, int port) {
			this.host = host;
			this.port = port;
		}
	}

	ServiceInfo(int appid, JSON json) throws Exception {
		this.appid = appid;
		for (JSON switcher : json.get("switchers").toArray())
			switchers.add(new SwitcherConfig(switcher.get("host").toString(), switcher.get("port").intValue()));
		JSON[] ja = json.get("pvids").toArray();
		pvids = new int[ja.length];
		for (int i = 0; i < ja.length; i++)
			pvids[i] = ja[i].intValue();
		ja = json.get("payids").toArray();
		payids = new int[ja.length];
		for (int i = 0; i < ja.length; i++)
			payids[i] = ja[i].intValue();
		ja = json.get("userjsons").toArray();
		userjsons = new JSON[ja.length];
		for (int i = 0; i < ja.length; i++)
			userjsons[i] = JSON.parse(ja[i].toString());
		running = json.get("running").booleanValue();
		optional = json.get("optional").toString();
	}

	SwitcherConfig randomSwitcherConfig() {
		return switchers.get(random.nextInt(switchers.size()));
	}

	public int[] getPvids() {
		return pvids;
	}

	public int[] getPayids() {
		return payids;
	}

	public JSON[] getUserJSONs() {
		return userjsons;
	}

	public boolean isRunning() {
		return running;
	}

	public String getOptional() {
		return optional;
	}
}