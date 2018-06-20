package limax.auany;

import java.util.Map;

import org.w3c.dom.Element;

import com.sun.net.httpserver.HttpHandler;

import limax.endpoint.AuanyService.Result;

public interface PayGateway {
	void initialize(Element e, Map<String, HttpHandler> httphandlers) throws Exception;

	void unInitialize();

	void onPay(long sessionid, int gateway, int payid, int product, int price, int quantity, String receipt,
			Result onresult) throws Exception;
}
