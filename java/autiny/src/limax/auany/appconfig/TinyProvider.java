package limax.auany.appconfig;

final class TinyProvider extends Provider {

	private String host = "";

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	TinyProvider(int id) {
		super(id, "");
	}

}
