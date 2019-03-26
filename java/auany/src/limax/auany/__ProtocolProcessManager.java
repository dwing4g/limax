package limax.auany;

import limax.auany.appconfig.AppManager;
import limax.auany.switcherauany.CheckProviderKey;
import limax.auany.switcherauany.Exchange;
import limax.auany.switcherauany.JSONPublish;
import limax.auany.switcherauany.OnlineAnnounce;
import limax.auany.switcherauany.PayAck;
import limax.auany.switcherauany.ProviderDown;
import limax.auany.switcherauany.SessionAuthByToken;
import limax.endpoint.AuanyService.Result;
import limax.switcher.LmkInfo;

public class __ProtocolProcessManager {
	private __ProtocolProcessManager() {
	}

	public static void process(CheckProviderKey rpc) {
		SessionManager.process(rpc);
	}

	public static void process(SessionAuthByToken rpc) {
		PlatManager.process(rpc);
	}

	public static void process(PayAck ack) {
		PayDelivery.ack(ack);
	}

	public static void process(OnlineAnnounce announce) throws Exception {
		SessionManager.process(announce);
		LmkManager.sendConfigData(announce.getTransport());
	}

	public static void process(ProviderDown pd) {
		SessionManager.process(pd);
	}

	public static void process(JSONPublish p) {
		AppManager.updateJSON(p.pvid, p.json);
	}

	public static void onPay(long sessionid, int gateway, int pvid, int product, int price, int quantity,
			String receipt, Result onresult) {
		PayManager.onPay(sessionid, gateway, pvid, product, price, quantity, receipt, onresult);
	}

	public static void onBind(long sessionid, String credential, String authcode, String username, String token,
			String platflag, Result onresult) {
		PlatManager.check(username, token, platflag, authcode, onresult,
				(uid, notAfter) -> Account.bind(credential, authcode, uid, notAfter, sessionid, onresult));
	}

	public static void onTemporaryFromCredential(String cred, String authcode, String authcode2, long milliseconds,
			byte usage, String subid, Result onresult) {
		Account.temporary(cred, authcode, authcode2, milliseconds, usage, subid, onresult);
	}

	public static void onTemporaryFromLogin(String username, String token, String platflag, int appid, String authcode,
			long milliseconds, byte usage, String subid, Result onresult) {
		PlatManager.check(username, token, platflag, authcode, onresult,
				(uid, notAfter) -> Account.temporary(uid, appid, authcode, milliseconds, usage, subid, onresult));
	}

	public static void onDerive(long sessionid, String credential, String authcode, Result onresult) {
		Account.derive(credential, authcode, sessionid, onresult);
	}

	public static void onTransfer(long sessionid, String username, String token, String platflag, String authcode,
			String temp, String authtemp, Result onresult) {
		PlatManager.check(username, token, platflag, authcode, onresult,
				(uid, notAfter) -> Account.transfer(uid, notAfter, authcode, temp, authtemp, sessionid, onresult));
	}

	public static void process(Exchange exchange) throws Exception {
		if (exchange.type == Exchange.UPLOAD_LMKDATA) {
			LmkInfo lmkInfo = new LmkInfo(exchange.data);
			Account.addLmkData(lmkInfo.getUid(), lmkInfo.getLmkData(), () -> {
			});
			exchange.data = new LmkInfo(lmkInfo.getUid()).encode();
			exchange.send(exchange.getTransport());
		}
	}
}
