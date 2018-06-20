package limax.auany;

import org.w3c.dom.Element;

import limax.auany.paygws.AppStore;

public interface PayLogger extends AutoCloseable {
	void initialize(Element e) throws Exception;

	void logCreate(PayOrder order);

	void logFake(long serial, int gateway, int expect);

	void logExpire(PayOrder order);

	void logOk(PayOrder order);

	void logFail(PayOrder order, String gatewayMessage);

	void logDead(PayDelivery pd);

	void logAppStoreCreate(AppStore.Request req, int gateway);

	void logAppStoreSucceed(AppStore.Request req);

	void logAppStoreFail(AppStore.Request req, int status);

	void logAppStoreReceiptReplay(AppStore.Request req);
}
