package limax.http;

import java.util.ArrayList;
import java.util.List;

public class Host {
	private final List<HttpContext> contexts = new ArrayList<>();

	Host() {
	}

	final static String normalizeDnsName(String dnsName) {
		return dnsName.trim().toLowerCase();
	}

	Handler find(String path) {
		return HttpContext.find(contexts, path);
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
}
