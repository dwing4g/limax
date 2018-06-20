package limax.auany.json;

import java.util.List;

import limax.codec.JSONSerializable;

public final class AppInfo implements JSONSerializable {
	@SuppressWarnings("unused")
	private final List<ServiceInfo> services;

	public AppInfo(List<ServiceInfo> services) {
		this.services = services;
	}
}
