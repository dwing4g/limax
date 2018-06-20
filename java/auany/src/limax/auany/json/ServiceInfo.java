package limax.auany.json;

import java.util.List;

import limax.codec.JSONSerializable;

public final class ServiceInfo implements JSONSerializable {
	@SuppressWarnings("unused")
	private final List<SwitcherInfo> switchers;
	@SuppressWarnings("unused")
	private final List<Integer> pvids;
	@SuppressWarnings("unused")
	private final List<Integer> payids;
	@SuppressWarnings("unused")
	private final List<String> userjsons;
	@SuppressWarnings("unused")
	private final boolean running;
	@SuppressWarnings("unused")
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
}
