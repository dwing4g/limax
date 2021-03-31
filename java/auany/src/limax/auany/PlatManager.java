package limax.auany;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import limax.auany.Account.LoginResult;
import limax.auany.appconfig.AppManager;
import limax.auany.switcherauany.SessionAuthByToken;
import limax.auanymonitor.AuthPlat;
import limax.codec.Octets;
import limax.codec.SHA256;
import limax.defines.ErrorCodes;
import limax.defines.ErrorSource;
import limax.defines.SessionFlags;
import limax.endpoint.AuanyService.Result;
import limax.http.HttpHandler;
import limax.switcher.LmkMasquerade;
import limax.switcherauany.AuanyAuthArg;
import limax.switcherauany.AuanyAuthRes;
import limax.util.ElementHelper;
import limax.util.Trace;

final class PlatManager {

	private static final long trustallmode_sessionid_step = 60 * 60 * 24 * 7;
	private static final long trustallmode_sessionid_init = buildTruskAllModeSessionidInit();
	private static final AtomicLong trustallmode_sessionid = new AtomicLong(trustallmode_sessionid_init);

	private static long buildTruskAllModeSessionidInit() {
		final Calendar cal = Calendar.getInstance();
		final long day = cal.get(Calendar.DAY_OF_WEEK) - 1;
		final long hour = cal.get(Calendar.HOUR_OF_DAY);
		final long minute = cal.get(Calendar.MINUTE);
		final long seconde = cal.get(Calendar.SECOND);
		return seconde + 60 * minute + 60 * 24 * hour + 60 * 60 * 24 * day;
	}

	private static long nextTruskAllModeSessionid() {
		return trustallmode_sessionid.getAndAdd(trustallmode_sessionid_step);
	}

	////////////////////////////////////////////////////////////////////////////////////////////

	private final static Map<String, PlatProcess> plats = new ConcurrentHashMap<>();
	private static boolean trustallmode = false;

	static void initialize(Element self, BiConsumer<String, HttpHandler> httphandlers) throws Exception {
		final boolean trustallmode = new ElementHelper(self).getBoolean("trustall", false);
		if (trustallmode) {
			Trace.fatal("PlatManager buildTruskAllMode sessionid init = " + trustallmode_sessionid_init + " step = "
					+ trustallmode_sessionid_step);
			PlatManager.trustallmode = trustallmode;
		} else {
			NodeList list = self.getElementsByTagName("plat");
			int count = list.getLength();
			for (int i = 0; i < count; i++)
				parsePlatElement((Element) list.item(i), httphandlers);
		}
	}

	static void check(String username, String token, String platflag, Result result) {
		String platname = platflag.toLowerCase();
		PlatProcess pp = plats.get(platname);
		if (pp == null)
			result.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_UNKNOWN_PLAT, "Plat " + platname + " not registered.");
		else {
			AuthPlat.increment_auth(platflag);
			pp.check(username, token, (errorSource, errorCode, uid) -> result.apply(errorSource, errorCode,
					uid.toLowerCase() + "@" + platname));
		}
	}

	static void check(String username, String token, String platflag, String authcode, Result result,
			BiConsumer<String, Long> lmkConsumer) {
		switch (platflag) {
		case "lmk":
			LmkMasquerade lmkMasquerade = LmkManager.getLmkMasquerade();
			if (lmkMasquerade == null) {
				result.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_UNKNOWN_PLAT, "LmkManager not enabled");
			} else {
				AuthPlat.increment_auth(platflag);
				if (!lmkMasquerade.masquerade(username, token,
						Octets.wrap(SHA256.digest(authcode.getBytes(StandardCharsets.UTF_8))), lmkConsumer))
					result.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_AUTHENTICATE_FAIL, "Invalid lmkBundle");
			}
			break;
		case "credential":
			if (username.charAt(0) == 'S') {
				AuthPlat.increment_auth(platflag);
				Account.check(username.substring(1), token, (errorSource, errorCode, uid, notAfter) -> {
					if (errorSource != ErrorSource.LIMAX || errorCode != ErrorCodes.SUCCEED)
						result.apply(errorSource, errorCode, "");
					else
						lmkConsumer.accept(uid, notAfter);
				});
			} else {
				result.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_AUTHENTICATE_FAIL,
						"Only session credential permitted");
			}
			break;
		default:
			check(username, token, platflag, (errorSource, errorCode, uid) -> {
				if (errorSource != ErrorSource.LIMAX || errorCode != ErrorCodes.SUCCEED) {
					result.apply(errorSource, errorCode, "Invalid username/token");
				} else {
					lmkConsumer.accept(uid, Long.MAX_VALUE);
				}
			});
		}
	}

	private static void parsePlatElement(Element e, BiConsumer<String, HttpHandler> httphandlers) throws Exception {
		ElementHelper eh = new ElementHelper(e);
		if (!eh.getBoolean("enable", false))
			return;
		String classname = eh.getString("className");
		if (classname.isEmpty())
			return;
		String platname = eh.getString("name").toUpperCase();
		if (Trace.isDebugEnabled())
			Trace.debug("Config.load plat classname = " + classname + " platname = " + platname);
		PlatProcess process = (PlatProcess) Class.forName(classname).getDeclaredConstructor().newInstance();
		process.init(e, httphandlers);
		if (null != plats.put(platname.toLowerCase(), process))
			throw new RuntimeException("duplicate plat process type " + platname);
	}

	private PlatManager() {
	}

	public static PlatProcess getPlatProcess(String platflag) {
		return plats.get(platflag.toLowerCase());
	}

	private static void response(SessionAuthByToken rpc) {
		try {
			rpc.response();
		} catch (Exception e) {
			if (Trace.isWarnEnabled())
				Trace.warn(rpc, e);
		}
	}

	private static LoginResult done(SessionAuthByToken rpc) {
		return (errorSource, errorCode, sessionid, mainid, uid, serial, lmkdata) -> {
			AuanyAuthRes res = rpc.getResult();
			res.errorSource = errorSource;
			res.errorCode = errorCode;
			res.sessionid = sessionid;
			res.mainid = mainid;
			res.uid = uid;
			if (!uid.isEmpty())
				res.flags |= SessionFlags.FLAG_ACCOUNT_BOUND;
			if (lmkdata != null && (res.flags & SessionFlags.FLAG_TEMPORARY_LOGIN) == 0)
				res.lmkdata = lmkdata;
			response(rpc);
		};
	}

	static void process(SessionAuthByToken rpc) {
		try {
			if (trustallmode)
				_trustAll(rpc);
			else
				_process(rpc);
		} catch (Exception e) {
			if (Trace.isWarnEnabled())
				Trace.warn(rpc, e);
			AuanyAuthRes res = rpc.getResult();
			res.errorSource = ErrorSource.LIMAX;
			res.errorCode = ErrorCodes.AUANY_AUTHENTICATE_FAIL;
			response(rpc);
		}
	}

	private static void _trustAll(SessionAuthByToken rpc) {
		AuanyAuthArg arg = rpc.getArgument();
		AuanyAuthRes res = rpc.getResult();
		SocketAddress peeraddress;
		@SuppressWarnings("unused")
		SocketAddress reportaddress;
		try (final ObjectInputStream ois = new ObjectInputStream(
				new ByteArrayInputStream(arg.clientaddress.array(), 0, arg.clientaddress.size()))) {
			peeraddress = (SocketAddress) ois.readObject();
			reportaddress = (SocketAddress) ois.readObject();
		} catch (Exception e) {
			peeraddress = reportaddress = new SocketAddress() {
				private static final long serialVersionUID = -210184168558204443L;
			};
		}
		if (!Firewall.checkPermit(peeraddress, arg.pvids.keySet())) {
			res.errorSource = ErrorSource.LIMAX;
			res.errorCode = ErrorCodes.AUANY_CHECK_LOGIN_IP_FAILED;
			response(rpc);
			return;
		}
		Integer appid = AppManager.checkAppId(arg.pvids.keySet());
		if (appid == null) {
			res.errorSource = ErrorSource.LIMAX;
			res.errorCode = ErrorCodes.AUANY_UNKNOWN_PLAT;
			response(rpc);
			return;
		}

		res.errorSource = ErrorSource.LIMAX;
		res.errorCode = ErrorCodes.SUCCEED;
		res.sessionid = res.mainid = nextTruskAllModeSessionid();
		res.uid = arg.username;
		response(rpc);
	}

	private static void _process(SessionAuthByToken rpc) {
		AuanyAuthArg arg = rpc.getArgument();
		AuanyAuthRes res = rpc.getResult();
		SocketAddress peeraddress;
		@SuppressWarnings("unused")
		SocketAddress reportaddress;
		try (final ObjectInputStream ois = new ObjectInputStream(
				new ByteArrayInputStream(arg.clientaddress.array(), 0, arg.clientaddress.size()))) {
			peeraddress = (SocketAddress) ois.readObject();
			reportaddress = (SocketAddress) ois.readObject();
		} catch (Exception e) {
			peeraddress = reportaddress = new SocketAddress() {
				private static final long serialVersionUID = -210184168558204443L;
			};
		}
		if (!Firewall.checkPermit(peeraddress, arg.pvids.keySet())) {
			res.errorSource = ErrorSource.LIMAX;
			res.errorCode = ErrorCodes.AUANY_CHECK_LOGIN_IP_FAILED;
			response(rpc);
			return;
		}
		String platflag;
		String subid;
		int pos = arg.platflag.indexOf(":");
		if (pos == -1) {
			platflag = arg.platflag.toLowerCase();
			subid = "";
		} else {
			platflag = arg.platflag.substring(0, pos).toLowerCase();
			subid = arg.platflag.substring(pos + 1);
		}
		Integer appid = AppManager.checkAppId(arg.pvids.keySet());
		if (appid == null) {
			if (platflag.equals("invite"))
				Invite.check(arg.username, arg.token, arg.pvids.keySet(), (errorSource, errorCode, uid) -> {
					res.errorSource = errorSource;
					res.errorCode = errorCode;
					res.sessionid = Long.parseLong(arg.username);
					response(rpc);
				});
			else {
				res.errorSource = ErrorSource.LIMAX;
				res.errorCode = ErrorCodes.AUANY_AUTHENTICATE_FAIL;
				response(rpc);
			}
			return;
		}
		switch (platflag) {
		case "lmk":
			if (LmkManager.getLmkMasquerade() != null)
				Account.login(arg.username, subid, appid, done(rpc));
			else {
				res.errorSource = ErrorSource.LIMAX;
				res.errorCode = ErrorCodes.AUANY_UNKNOWN_PLAT;
				response(rpc);
			}
			return;
		case "credential":
			switch (arg.username.charAt(0)) {
			case 'S':
				Account.login(arg.username.substring(1), arg.token, subid, appid, done(rpc));
				break;
			case 'T':
				res.flags |= SessionFlags.FLAG_TEMPORARY_LOGIN;
				Account.temporaryLogin(arg.username.substring(1), arg.token, appid, done(rpc));
				break;
			default:
				res.errorSource = ErrorSource.LIMAX;
				res.errorCode = ErrorCodes.AUANY_SERVICE_INVALID_CREDENTIAL;
				response(rpc);
			}
			return;
		default:
			check(arg.username, arg.token, platflag, (errorSource, errorCode, uid) -> {
				if (errorSource != ErrorSource.LIMAX || errorCode != ErrorCodes.SUCCEED) {
					res.errorSource = errorSource;
					res.errorCode = errorCode;
					response(rpc);
				} else {
					if ("portforward".equals(platflag))
						res.flags |= SessionFlags.FLAG_CAN_PORT_FORWARD;
					Account.login(uid, subid, appid, done(rpc));
				}
			});
		}
	}
}
