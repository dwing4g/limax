package limax.auany;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import limax.auany.paygws.AppStore.Request;
import limax.defines.ErrorCodes;
import limax.defines.ErrorSource;
import limax.endpoint.AuanyService.Result;
import limax.http.HttpHandler;
import limax.util.ElementHelper;
import limax.util.Helper;
import limax.util.Trace;
import limax.util.XMLUtils;

public final class PayManager {
	private static boolean enabled = false;
	private final static List<PayLogger> loggers = new ArrayList<>();
	private final static PayLogger payLogger = new PayLogger() {
		@Override
		public void close() throws Exception {
			for (PayLogger logger : loggers)
				logger.close();
		}

		@Override
		public void initialize(Element e) {
		}

		@Override
		public void logCreate(PayOrder order) {
			loggers.forEach(logger -> logger.logCreate(order));
		}

		@Override
		public void logFake(long serial, int gateway, int expect) {
			loggers.forEach(logger -> logger.logFake(serial, gateway, expect));
		}

		@Override
		public void logExpire(PayOrder order) {
			loggers.forEach(logger -> logger.logExpire(order));
		}

		@Override
		public void logOk(PayOrder order) {
			loggers.forEach(logger -> logger.logOk(order));
		}

		@Override
		public void logFail(PayOrder order, String gatewayMessage) {
			loggers.forEach(logger -> logger.logFail(order, gatewayMessage));
		}

		@Override
		public void logDead(PayDelivery pd) {
			loggers.forEach(logger -> logger.logDead(pd));
		}

		@Override
		public void logAppStoreCreate(Request req, int gateway) {
			loggers.forEach(logger -> logger.logAppStoreCreate(req, gateway));
		}

		@Override
		public void logAppStoreSucceed(Request req) {
			loggers.forEach(logger -> logger.logAppStoreSucceed(req));
		}

		@Override
		public void logAppStoreFail(Request req, int status) {
			loggers.forEach(logger -> logger.logAppStoreFail(req, status));
		}

		@Override
		public void logAppStoreReceiptReplay(Request req) {
			loggers.forEach(logger -> logger.logAppStoreReceiptReplay(req));
		}
	};
	private final static Map<Integer, PayGateway> gateways = new HashMap<>();

	static void initialize(Element self, BiConsumer<String, HttpHandler> httphandlers) throws Exception {
		ElementHelper eh = new ElementHelper(self);
		boolean payEnable = eh.getBoolean("enable", false);
		if (payEnable) {
			Path orderQueueHome = Paths.get(eh.getString("orderQueueHome", "queue"));
			Path deliveryQueueHome = Paths.get(eh.getString("deliveryQueueHome", "queue"));
			int orderQueueConcurrencyBits = eh.getInt("orderQueueConcurrencyBits", 3);
			int deliveryQueueConcurrencyBits = eh.getInt("deliveryQueueConcurrencyBits", 3);
			long orderExpire = eh.getLong("orderExpire", 3600000l);
			long deliveryExpire = eh.getLong("deliveryExpire", 604800000l);
			long deliveryQueueCheckPeriod = eh.getLong("deliveryQueueCheckPeriod", 60000l);
			int deliveryQueueBackoffMax = eh.getInt("deliveryQueueBackoffMax", 5);
			int deliveryQueueScheduler = eh.getInt("deliveryQueueScheduler", 4);
			for (Element e : XMLUtils.getChildElements(self).stream()
					.filter(node -> node.getNodeName().equals("logger")).toArray(Element[]::new)) {
				Class<?> clazz = Class.forName(new ElementHelper(e).getString("className"));
				if (Helper.interfaceSet(clazz).contains(PayLogger.class)) {
					PayLogger logger = (PayLogger) clazz.getDeclaredConstructor().newInstance();
					logger.initialize(e);
					loggers.add(logger);
					if (Trace.isInfoEnabled())
						Trace.info("load PayLogger " + clazz.getName());
				}
			}
			FileBundle.initialize(Paths.get(eh.getString("fileTransactionHome", "transactions")));
			PayDelivery.initialize(deliveryQueueHome, deliveryQueueConcurrencyBits, deliveryExpire,
					deliveryQueueCheckPeriod, deliveryQueueBackoffMax, deliveryQueueScheduler);
			PayOrder.initialize(orderQueueHome, orderQueueConcurrencyBits, orderExpire);
			NodeList list = self.getElementsByTagName("pay");
			int count = list.getLength();
			for (int i = 0; i < count; i++)
				parsePayElement((Element) list.item(i), httphandlers);
			enabled = true;
			eh.warnUnused("parserClass");
		}
	}

	static void unInitialize() {
		enabled = false;
		for (PayGateway gw : gateways.values()) {
			try {
				gw.unInitialize();
			} catch (Exception e) {
			}
		}
		PayOrder.unInitialize();
		PayDelivery.unInitialize();
		FileBundle.unInitialize();
		try {
			payLogger.close();
		} catch (Exception e) {
		}
	}

	private static void parsePayElement(Element e, BiConsumer<String, HttpHandler> httphandlers) throws Exception {
		ElementHelper eh = new ElementHelper(e);
		int gateway = eh.getInt("gateway");
		PayGateway payGateway = (PayGateway) Class.forName(eh.getString("className")).getDeclaredConstructor()
				.newInstance();
		payGateway.initialize(e, httphandlers);
		gateways.put(gateway, payGateway);
	}

	static void onPay(long sessionid, int gateway, int payid, int product, int price, int quantity, String receipt,
			Result onresult) {
		if (!enabled) {
			onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_PAY_NOT_ENABLED, "");
			return;
		}
		PayGateway gw = gateways.get(gateway);
		if (gw == null) {
			onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_PAY_GATEWAY_NOT_DEFINED, "");
			return;
		}
		try {
			gw.onPay(sessionid, gateway, payid, product, price, quantity, receipt, onresult);
		} catch (Exception e) {
			onresult.apply(ErrorSource.LIMAX, ErrorCodes.AUANY_SERVICE_PAY_GATEWAY_FAIL, "");
			if (Trace.isInfoEnabled())
				Trace.info("PayManager.onPay", e);
		}
	}

	public static PayLogger getLogger() {
		return payLogger;
	}
}
