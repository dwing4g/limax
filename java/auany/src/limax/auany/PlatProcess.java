package limax.auany;

import java.util.function.BiConsumer;

import org.w3c.dom.Element;

import limax.endpoint.AuanyService.Result;
import limax.http.HttpHandler;

public interface PlatProcess {

	void init(Element e, BiConsumer<String, HttpHandler> httphandlers);

	void check(String username, String token, Result result);
}
