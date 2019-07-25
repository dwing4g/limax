package limax.auany;

import limax.endpoint.AuanyService.Result;

public final class Auany {

	private Auany() {
	}

	public static void firewallReload() {
		Firewall.reload();
	}

	public static void platCheck(String username, String token, String platflag, Result result) {
		PlatManager.check(username, token, platflag, result);
	}
}
