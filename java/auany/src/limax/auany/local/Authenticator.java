package limax.auany.local;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.w3c.dom.Element;

import limax.auany.PlatProcess;
import limax.defines.ErrorCodes;
import limax.defines.ErrorSource;
import limax.endpoint.AuanyService.Result;
import limax.http.HttpHandler;
import limax.util.ConcurrentEnvironment;
import limax.util.ElementHelper;
import limax.util.Trace;
import limax.util.XMLUtils;
import limax.xmlconfig.Service;

public class Authenticator implements PlatProcess {
	private final List<Authenticate> auths = new ArrayList<>();
	private final AtomicInteger index = new AtomicInteger();

	private class RequestContext {
		private final String username;
		private final String password;
		private final Consumer<Authenticate.Result> response;
		private final int start;
		private volatile int cur = 0;

		RequestContext(String username, String password, Consumer<Authenticate.Result> response, int start) {
			this.username = username;
			this.password = password;
			this.response = response;
			this.start = start;
		}

		void action() {
			auths.get((cur + start) % auths.size()).access(username, password, r -> {
				if (r == Authenticate.Result.Accept || r == Authenticate.Result.Reject || ++cur == auths.size())
					response.accept(r);
				else
					action();
			});
		}
	}

	@Override
	public void init(Element ele, BiConsumer<String, HttpHandler> httphandlers) {
		ElementHelper eh = new ElementHelper(ele);
		int timeout = eh.getInt("timeout", 2000);
		String name = eh.getString("name");
		ScheduledExecutorService scheduler = ConcurrentEnvironment.getInstance()
				.newScheduledThreadPool(getClass().getName() + "." + name, eh.getInt("scheduler", 8));
		try {
			for (Element e : XMLUtils.getChildElements(ele)) {
				eh = new ElementHelper(e);
				switch (e.getTagName()) {
				case "radius":
					auths.add(new Radius(scheduler, eh.getString("host"), eh.getInt("port", 1812), timeout, "auany",
							eh.getString("secret")));
					break;
				case "ldap":
					auths.add(new Ldap(scheduler, eh.getString("url"), eh.getString("baseDN"), eh.getString("key"),
							timeout));
					break;
				case "sql":
					auths.add(new Sql(scheduler, eh.getString("url"), eh.getInt("pool", 5), eh.getString("opClassName"),
							timeout));
					break;
				default:
					throw new IllegalArgumentException("unknown local authenticator " + e.getTagName());
				}
				eh.warnUnused();
			}
		} catch (Exception e) {
			if (Trace.isErrorEnabled())
				Trace.error("Authenticator init", e);
			ConcurrentEnvironment.getInstance().shutdown(getClass().getName());
			if (e instanceof IllegalArgumentException)
				throw (IllegalArgumentException) e;
			throw new IllegalArgumentException(e);
		}
		Service.addRunAfterEngineStopTask(() -> {
			auths.forEach(Authenticate::stop);
			ConcurrentEnvironment.getInstance().shutdown(getClass().getName());
		});
	}

	@Override
	public void check(String username, String token, Result result) {
		new RequestContext(username, token, r -> {
			int errorCode = 0;
			switch (r) {
			case Accept:
				errorCode = ErrorCodes.SUCCEED;
				break;
			case Reject:
				errorCode = ErrorCodes.AUANY_BAD_TOKEN;
				break;
			case Timeout:
				errorCode = ErrorCodes.AUANY_AUTHENTICATE_TIMEOUT;
				break;
			case Fail:
				errorCode = ErrorCodes.AUANY_AUTHENTICATE_FAIL;
			}
			result.apply(ErrorSource.LIMAX, errorCode, username);
		}, index.getAndIncrement()).action();
	}
}
