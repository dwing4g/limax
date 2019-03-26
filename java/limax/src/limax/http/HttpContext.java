package limax.http;

import java.net.URI;
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

	HttpContext(String path, Handler handler) {
		this.prefix = fix(path);
		this.uri = URI.create(prefix.substring(0, prefix.length() - 1));
		this.handler = handler;
	}

	static void create(List<HttpContext> contexts, String path, Handler handler) {
		synchronized (contexts) {
			contexts.add(new HttpContext(path, handler));
			contexts.sort((o1, o2) -> o2.prefix.length() - o1.prefix.length());
		}
	}

	static void remove(List<HttpContext> contexts, String path) {
		path = fix(path);
		int len = path.length();
		synchronized (contexts) {
			for (Iterator<HttpContext> it = contexts.iterator(); it.hasNext();) {
				String prefix = it.next().prefix;
				int prefixLen = prefix.length();
				if (prefixLen > len)
					continue;
				if (prefixLen == len) {
					if (prefix.equals(path))
						it.remove();
					continue;
				}
				break;
			}
		}
	}

	static HttpContext find(List<HttpContext> contexts, String path) {
		path = fix(path);
		int len = path.length();
		synchronized (contexts) {
			for (Iterator<HttpContext> it = contexts.iterator(); it.hasNext();) {
				HttpContext c = it.next();
				String prefix = c.prefix;
				if (prefix.length() > len)
					continue;
				if (path.startsWith(prefix))
					return c;
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
