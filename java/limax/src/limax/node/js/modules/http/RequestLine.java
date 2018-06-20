package limax.node.js.modules.http;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RequestLine {
	private final static Pattern pattern = Pattern.compile(
			"(ACL|BIND|CHECKOUT|CONNECT|COPY|DELETE|GET|HEAD|LINK|LOCK|M-SEARCH|MERGE|MKACTIVITY|MKCALENDAR|MKCOL|MOVE|NOTIFY|OPTIONS|PATCH|POST|PROPFIND|PROPPATCH|PURGE|PUT|REBIND|REPORT|SEARCH|SUBSCRIBE|TRACE|UNBIND|UNLINK|UNLOCK|UNSUBSCRIBE)\\s+(\\S+)\\s+HTTP/(\\d)\\.(\\d)",
			Pattern.CASE_INSENSITIVE);

	private final String method;
	private final URI target;
	private final int majorVersion;
	private final int minorVersion;

	RequestLine(String line) throws HttpException {
		Matcher matcher = pattern.matcher(line);
		if (!matcher.matches())
			throw new HttpException(HttpURLConnection.HTTP_NOT_IMPLEMENTED, line);
		try {
			this.method = matcher.group(1).toUpperCase();
			this.target = new URI(matcher.group(2));
			this.majorVersion = Integer.parseInt(matcher.group(3));
			this.minorVersion = Integer.parseInt(matcher.group(4));
		} catch (Exception e) {
			throw new HttpException(HttpURLConnection.HTTP_NOT_IMPLEMENTED, e);
		}
	}

	public String getMethod() {
		return method;
	}

	public URI getTarget() {
		return target;
	}

	public int getMajorVersion() {
		return majorVersion;
	}

	public int getMinorVersion() {
		return minorVersion;
	}

	public String getVersion() {
		return majorVersion + "." + minorVersion;
	}
}
