package limax.endpoint;

import limax.codec.Octets;

public final class LoginConfig {
	private final String username;
	private final String token;
	private final String platflag;
	private final LmkBundle lmkBundle;
	private final String subid;
	private final ProviderLoginDataManager pldm;
	private volatile LmkUpdater lmkUpdater;

	private LoginConfig(String username, String token, String platflag, LmkBundle lmkBundle, String subid,
			ProviderLoginDataManager pldm) {
		this.username = username;
		this.token = token;
		this.platflag = platflag;
		this.lmkBundle = lmkBundle;
		this.subid = subid;
		this.pldm = pldm;
	}

	public static LoginConfig plainLogin(String username, String token, String platflag, String subid,
			ProviderLoginDataManager pldm) {
		return new LoginConfig(username, token, platflag, null, subid, pldm);
	}

	public static LoginConfig plainLogin(String username, String token, String platflag, String subid) {
		return new LoginConfig(username, token, platflag, null, subid, null);
	}

	public static LoginConfig plainLogin(String username, String token, String platflag,
			ProviderLoginDataManager pldm) {
		return new LoginConfig(username, token, platflag, null, "", pldm);
	}

	public static LoginConfig plainLogin(String username, String token, String platflag) {
		return new LoginConfig(username, token, platflag, null, "", null);
	}

	private static String decodeMainCredential(String credential) {
		int pos = credential.indexOf(',');
		return pos == -1 ? credential : credential.substring(0, pos);
	}

	public static LoginConfig credentialLogin(String credential, String authcode, String subid,
			ProviderLoginDataManager pldm) {
		return new LoginConfig(decodeMainCredential(credential), authcode, "credential", null, subid, pldm);
	}

	public static LoginConfig credentialLogin(String credential, String authcode, String subid) {
		return new LoginConfig(decodeMainCredential(credential), authcode, "credential", null, subid, null);
	}

	public static LoginConfig credentialLogin(String credential, String authcode, ProviderLoginDataManager pldm) {
		return new LoginConfig(decodeMainCredential(credential), authcode, "credential", null, "", pldm);
	}

	public static LoginConfig credentialLogin(String credential, String authcode) {
		return new LoginConfig(decodeMainCredential(credential), authcode, "credential", null, "", null);
	}

	public static LoginConfig lmkLogin(LmkBundle lmkBundle, String subid, ProviderLoginDataManager pldm) {
		return new LoginConfig(null, null, "lmk", lmkBundle, subid, pldm);
	}

	public static LoginConfig lmkLogin(LmkBundle lmkBundle, String subid) {
		return new LoginConfig(null, null, "lmk", lmkBundle, subid, null);
	}

	public static LoginConfig lmkLogin(LmkBundle lmkBundle, ProviderLoginDataManager pldm) {
		return new LoginConfig(null, null, "lmk", lmkBundle, "", pldm);
	}

	public static LoginConfig lmkLogin(LmkBundle lmkBundle) {
		return new LoginConfig(null, null, "lmk", lmkBundle, "", null);
	}

	String getUsername() {
		return lmkBundle == null ? username : lmkBundle.x509();
	}

	String getToken(Octets nonce) {
		return lmkBundle == null ? token : lmkBundle.sign(nonce);
	}

	String getPlatflagRaw() {
		return platflag;
	}

	String getPlatflag() {
		return subid.isEmpty() ? getPlatflagRaw() : getPlatflagRaw() + ":" + subid;
	}

	ProviderLoginDataManager getProviderLoginDataManager() {
		return pldm;
	}

	public void setLmkUpdater(LmkUpdater lmkUpdater) {
		this.lmkUpdater = lmkUpdater;
	}

	LmkUpdater getLmkUpdater() {
		return lmkUpdater;
	}
}
