package limax.auany;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import org.w3c.dom.Element;

import limax.http.HttpHandler;
import limax.util.ElementHelper;
import limax.util.Helper;
import limax.util.Trace;
import limax.util.XMLUtils;

public class AccountManager {
	private final static List<AccountLogger> loggers = new ArrayList<>();
	private final static AccountLogger accountLogger = new AccountLogger() {
		@Override
		public void close() throws Exception {
			for (AccountLogger logger : loggers)
				logger.close();
		}

		@Override
		public void initialize(Element e) {
		}

		@Override
		public void link(int appid, long sessionid, String uid) {
			loggers.forEach(logger -> logger.link(appid, sessionid, uid));
		}

		@Override
		public void relink(int appid, long sessionid, String uidsrc, String uiddst) {
			loggers.forEach(logger -> logger.relink(appid, sessionid, uidsrc, uiddst));
		}
	};

	static void initialize(Element self, BiConsumer<String, HttpHandler> httphandlers) throws Exception {
		for (Element e : XMLUtils.getChildElements(self).stream()
				.filter(node -> node.getAttribute("enable").equals("true")).toArray(Element[]::new)) {
			Class<?> clazz = Class.forName(new ElementHelper(e).getString("className"));
			if (Helper.interfaceSet(clazz).contains(AccountLogger.class)) {
				AccountLogger logger = (AccountLogger) clazz.getDeclaredConstructor().newInstance();
				logger.initialize(e);
				loggers.add(logger);
				if (Trace.isInfoEnabled())
					Trace.info("load AccountLogger " + clazz.getName());
			}
		}
	}

	static void unInitialize() {
		try {
			accountLogger.close();
		} catch (Exception e) {
		}
	}

	static AccountLogger getLogger() {
		return accountLogger;
	}
}
