package limax.auany;

import java.util.function.BiConsumer;

import org.w3c.dom.Element;

import limax.endpoint.AuanyService.Result;
import limax.http.HttpHandler;

public interface PayGateway {
	void initialize(Element e, BiConsumer<String, HttpHandler> httphandlers) throws Exception;

	void unInitialize();

	void onPay(long sessionid, int gateway, int payid, int product, int price, int quantity, String receipt,
			Result onresult) throws Exception;
}
