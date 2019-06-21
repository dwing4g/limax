package limax.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;

class HttpContext {
	private final String prefix;
	private final URI uri;
	private final Handler handler;

	private static String fix(String path) {
		if (path.charAt(0) != '/')
			throw new IllegalArgumentException("Illegal value for path");
		return (path.charAt(path.length() - 1) == '/' ? path : path + "/").toLowerCase();
	}

	private static String normalize(String path) {
		try {
			return fix(new URI(null, null, path, null).normalize().getPath());
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}

	HttpContext(String path, Handler handler) {
		this.prefix = normalize(path);
		try {
			this.uri = URI.create(new URI(null, null, prefix.substring(0, prefix.length() - 1), null).toASCIIString());
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
		this.handler = handler;
	}

	static void create(List<HttpContext> contexts, String path, Handler handler) {
		synchronized (contexts) {
			contexts.add(new HttpContext(path, handler));
			contexts.sort((o1, o2) -> o2.prefix.length() - o1.prefix.length());
		}
	}

	static void remove(List<HttpContext> contexts, String path) {
		path = normalize(path);
		int pathLen = path.length();
		synchronized (contexts) {
			for (Iterator<HttpContext> it = contexts.iterator(); it.hasNext();) {
				String prefix = it.next().prefix;
				int prefixLen = prefix.length();
				if (prefixLen > pathLen)
					continue;
				if (prefixLen == pathLen) {
					if (!prefix.equals(path))
						continue;
					it.remove();
				}
				break;
			}
		}
	}

	static HttpContext find(List<HttpContext> contexts, String path) {
		path = fix(path);
		int pathLen = path.length();
		synchronized (contexts) {
			for (Iterator<HttpContext> it = contexts.iterator(); it.hasNext();) {
				HttpContext ctx = it.next();
				String prefix = ctx.prefix;
				if (prefix.length() > pathLen)
					continue;
				if (path.startsWith(prefix))
					return ctx;
			}
		}
		return null;
	}

	Handler handler() {
		return handler;
	}

	URI uri() {
		return uri;
	}
}
