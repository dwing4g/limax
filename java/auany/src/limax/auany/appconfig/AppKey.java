package limax.auany.appconfig;

import java.util.Objects;

public final class AppKey {
	private final ServiceType type;
	private final int appid;

	public AppKey(ServiceType type, int appid) {
		this.type = type;
		this.appid = appid;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AppKey))
			return false;
		AppKey r = (AppKey) o;
		return r.type == type && r.appid == appid;
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, appid);
	}

	public ServiceType getType() {
		return type;
	}

	public int getAppId() {
		return appid;
	}
}