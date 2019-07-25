package limax.auany;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.w3c.dom.Element;

import limax.auany.appconfig.AppManager;
import limax.auany.json.InviteCode;
import limax.defines.ErrorCodes;
import limax.defines.ErrorSource;
import limax.endpoint.AuanyService.Result;
import limax.http.HttpHandler;
import limax.util.ElementHelper;

class Invite {
	private static long LIFETIME;
	private final static RuntimeException exception = new RuntimeException();
	private final static Map<Long, Long> map0 = new LinkedHashMap<>();
	private final static Map<Long, Long> map1 = new LinkedHashMap<>();

	private static void cleanup(long now) {
		for (Iterator<Map.Entry<Long, Long>> it = map0.entrySet().iterator(); it.hasNext()
				&& now - it.next().getValue() > LIFETIME;)
			it.remove();
		for (Iterator<Map.Entry<Long, Long>> it = map1.entrySet().iterator(); it.hasNext()
				&& now - it.next().getValue() > LIFETIME;)
			it.remove();
	}

	private static long alloc(int appid) {
		synchronized (exception) {
			while (true) {
				long now = System.nanoTime();
				cleanup(now);
				long code = Long.MIN_VALUE | ((long) Long.hashCode(now) << 32) | appid;
				if (map0.putIfAbsent(code, now) == null)
					return code;
			}
		}
	}

	private static boolean test0(long code) {
		synchronized (exception) {
			long now = System.nanoTime();
			cleanup(now);
			if (map0.remove(code) == null)
				return false;
			map1.put(code, now);
			return true;
		}
	}

	public static boolean test1(long code) {
		synchronized (exception) {
			cleanup(System.nanoTime());
			return map1.remove(code) != null;
		}
	}

	public static void init(Element e, BiConsumer<String, HttpHandler> httphandlers) {
		ElementHelper eh = new ElementHelper(e);
		LIFETIME = eh.getLong("inviteExpire", 60000l) * 1000000l;
		final HttpHandler handler = HttpHelper
				.createHttpHandler(HttpHelper.makeJSONCacheNone(HttpHelper.uri2AppKey("/invite"),
						key -> new InviteCode(AppManager.randomSwitcher(key.getType(), key.getAppId()),
								alloc(key.getAppId()))));
		httphandlers.accept("/invite", handler);
	}

	public static void check(String username, String token, Set<Integer> pvids, Result result) {
		try {
			long code = Long.parseLong(username);
			if (pvids.size() != 1 || pvids.iterator().next() != SessionManager.providerId)
				throw exception;
			if (!test0(code))
				throw exception;
			result.apply(ErrorSource.LIMAX, ErrorCodes.SUCCEED, username);
		} catch (Throwable t) {
			result.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_AUTHENTICATE_FAIL, "");
		}
	}
}
