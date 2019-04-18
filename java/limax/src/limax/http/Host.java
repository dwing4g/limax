package limax.http;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import limax.http.HttpServer.Parameter;

public class Host {
	private final String name;
	private final List<HttpContext> contexts = new ArrayList<>();
	private final EnumMap<Parameter, HttpContext> map = new EnumMap<>(Parameter.class);

	Host(String name) {
		this.name = name;
		map.put(Parameter.HANDLER_403, toContext(Parameter.HANDLER_403.def()));
		map.put(Parameter.HANDLER_404, toContext(Parameter.HANDLER_404.def()));
		map.put(Parameter.HANDLER_ASTERISK, toContext(Parameter.HANDLER_ASTERISK.def()));
	}

	private static HttpContext toContext(Object value) {
		return new HttpContext("/", (HttpHandler) value);
	}

	final static String normalizeDnsName(String dnsName) {
		return dnsName.trim().toLowerCase();
	}

	HttpContext find(String path) {
		HttpContext ctx = HttpContext.find(contexts, path);
		return ctx != null ? ctx
				: path.equals("*") ? map.get(Parameter.HANDLER_ASTERISK) : map.get(Parameter.HANDLER_404);
	}

	public String name() {
		return name;
	}

	public void createContext(String path, HttpHandler handler) {
		HttpContext.create(contexts, path, handler);
	}

	public void createContext(String path, WebSocketHandler handler) {
		HttpContext.create(contexts, path, handler);
	}

	public void removeContext(String path) {
		HttpContext.remove(contexts, path);
	}

	public Object get(Parameter key) {
		HttpContext ctx = map.get(key);
		return ctx == null ? null : ctx.handler();
	}

	public Object set(Parameter key, Object value) {
		Object ctx = map.get(key);
		return ctx == null ? null : map.put(key, toContext(value)).handler();
	}
}
