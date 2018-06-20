package limax.auany.auanyviews;

import limax.auany.__ProtocolProcessManager;
import limax.codec.JSON;
import limax.defines.ErrorCodes;
import limax.defines.ErrorSource;
import limax.endpoint.AuanyService.Result;
import limax.provider.GlobalView;
import limax.util.Trace;

public final class Service extends limax.auany.auanyviews._Service {

	private Service(GlobalView.CreateParameter param) {
		super(param);
	}

	@Override
	protected void onClose() {
	}

	@Override
	protected void onControl(_Service.Pay param, long sessionid) {
		String info = "";
		if (Trace.isInfoEnabled())
			info = "payorder!" + param.gateway + "," + param.payid + "," + param.product + "," + param.price + ","
					+ param.quantity + "," + param.receipt;
		__ProtocolProcessManager.onPay(sessionid, param.gateway, param.payid, param.product, param.price,
				param.quantity, param.receipt, cbResult(sessionid, param.sn, info));
	}

	@Override
	protected void onControl(_Service.Derive param, long sessionid) {
		String info = "";
		if (Trace.isInfoEnabled())
			info = "derive!" + param.credential + "," + param.authcode;
		__ProtocolProcessManager.onDerive(sessionid, param.credential, param.authcode,
				cbResult(sessionid, param.sn, info));
	}

	@Override
	protected void onControl(_Service.Bind param, long sessionid) {
		String info = "";
		if (Trace.isInfoEnabled())
			info = "bind!" + param.credential + "," + param.authcode + "," + param.username + "," + param.token + ","
					+ param.platflag;
		__ProtocolProcessManager.onBind(sessionid, param.credential, param.authcode, param.username, param.token,
				param.platflag, cbResult(sessionid, param.sn, info));
	}

	@Override
	protected void onControl(_Service.TemporaryFromCredential param, long sessionid) {
		String info = "";
		if (Trace.isInfoEnabled())
			info = "temporaryFromCredential!" + param.credential + "," + param.authcode + "," + param.authcode2 + ","
					+ param.millisecond + "," + param.usage + "," + param.subid;
		__ProtocolProcessManager.onTemporaryFromCredential(param.credential, param.authcode, param.authcode2,
				param.millisecond, param.usage, param.subid, cbResult(sessionid, param.sn, info));
	}

	@Override
	protected void onControl(_Service.TemporaryFromLogin param, long sessionid) {
		String info = "";
		if (Trace.isInfoEnabled())
			info = "temporaryFromLogin!" + param.username + "," + param.token + "," + param.platflag + "," + param.appid
					+ "," + param.authcode + param.millisecond + "," + param.usage + "," + param.subid;
		__ProtocolProcessManager.onTemporaryFromLogin(param.username, param.token, param.platflag, param.appid,
				param.authcode, param.millisecond, param.usage, param.subid, cbResult(sessionid, param.sn, info));
	}

	@Override
	protected void onControl(_Service.Transfer param, long sessionid) {
		String info = "";
		if (Trace.isInfoEnabled())
			info = "transfer!" + param.username + "," + param.token + "," + param.platflag + "," + param.authcode + ","
					+ param.temp + "," + param.authtemp;
		__ProtocolProcessManager.onTransfer(sessionid, param.username, param.token, param.platflag, param.authcode,
				param.temp, param.authtemp, cbResult(sessionid, param.sn, info));
	}

	@Override
	protected void onMessage(String message, long sessionid) {
		try {
			JSON json = JSON.parse(message);
			Result onresult = cbResult(sessionid, json.get("sn").intValue(), message);
			try {
				switch (json.get("cmd").toString()) {
				case "pay":
					__ProtocolProcessManager.onPay(sessionid, json.get("gateway").intValue(),
							json.get("payid").intValue(), json.get("product").intValue(), json.get("price").intValue(),
							json.get("quantity").intValue(), json.get("receipt").toString(), onresult);
					return;
				case "temporaryFromLogin":
					__ProtocolProcessManager.onTemporaryFromLogin(json.get("username").toString(),
							json.get("token").toString(), json.get("platflag").toString(), json.get("appid").intValue(),
							json.get("authcode").toString(), json.get("millisecond").longValue(),
							(byte) json.get("usage").intValue(), json.get("subid").toString(), onresult);
					return;
				case "transfer":
					__ProtocolProcessManager.onTransfer(sessionid, json.get("username").toString(),
							json.get("token").toString(), json.get("platflag").toString(),
							json.get("authcode").toString(), json.get("temp").toString(),
							json.get("authtemp").toString(), onresult);

				}
			} catch (Exception e) {
				onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_BAD_ARGS, "");
			}
		} catch (Exception e) {
		}
	}

	private static Result cbResult(long sessionid, int sn, String info) {
		return (errorSource, errorCode, result) -> {
			ServiceResult view = ServiceResult.createLooseInstance();
			view.info = info;
			view._setResult(new limax.auanyviews.Result(sn, errorSource, errorCode, result));
			view.getMembership().add(sessionid);
		};
	}
}
