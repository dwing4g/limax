package limax.node.js.modules;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import limax.node.js.EventLoop;
import limax.node.js.Module;

public final class Url implements Module {
	public Url(EventLoop eventLoop) {
	}

	public Map<String, Object> parse(String url) throws Exception {
		URI uri = new URI(url).normalize();
		String scheme = uri.getScheme();
		if (scheme != null)
			scheme = scheme.toLowerCase();
		String userInfo = uri.getUserInfo();
		String host = uri.getHost();
		if (host != null)
			host = host.toLowerCase();
		int port = uri.getPort();
		String path = uri.getPath();
		if (path.isEmpty())
			path = "/";
		String query = uri.getQuery();
		String fragment = uri.getFragment();
		uri = new URI(scheme, userInfo, host, port, path, query, fragment);
		Map<String, Object> map = new HashMap<>();
		map.put("href", uri.toString());
		map.put("protocol", scheme != null ? scheme + ":" : null);
		map.put("slashes", uri.getAuthority() != null);
		map.put("host", port != -1 ? host + ":" + port : host);
		map.put("auth", userInfo);
		map.put("hostname", host);
		map.put("port", port != -1 ? port : null);
		map.put("pathname", path);
		map.put("search", query != null ? "?" + query : null);
		map.put("path", path + (query != null ? "?" + query : ""));
		map.put("query", query);
		map.put("hash", fragment != null ? "#" + fragment : null);
		return map;
	}

	public String resolve(String from, String to) throws Exception {
		return new URI(from).resolve(to).toString();
	}
}
