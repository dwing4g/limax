package limax.auany;

import java.util.Map;

import org.w3c.dom.Element;

import com.sun.net.httpserver.HttpHandler;

import limax.endpoint.AuanyService.Result;

public interface PlatProcess {

	void init(Element e, Map<String, HttpHandler> httphandlers);

	void check(String username, String token, Result result);
}
