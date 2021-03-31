package limax.auany.json;

import java.util.List;

import limax.codec.JSONSerializable;

public final class ServiceInfo implements JSONSerializable {
	private final List<SwitcherInfo> switchers;
	private final List<Integer> pvids;
	private final List<Integer> payids;
	private final List<String> userjsons;
	private final boolean running;
	private final String optional;

	public ServiceInfo(List<SwitcherInfo> switchers, List<Integer> pvids, List<Integer> payids, List<String> userjsons,
			boolean running, String optional) {
		this.switchers = switchers;
		this.pvids = pvids;
		this.payids = payids;
		this.userjsons = userjsons;
		this.running = running;
		this.optional = optional;
	}

	public List<SwitcherInfo> getSwitchers() {
		return switchers;
	}

	public List<Integer> getPvids() {
		return pvids;
	}

	public List<Integer> getPayids() {
		return payids;
	}

	public List<String> getUserjsons() {
		return userjsons;
	}

	public boolean isRunning() {
		return running;
	}

	public String getOptional() {
		return optional;
	}

}
