package limax.http;

import java.util.HashMap;
import java.util.Map;

enum HttpVersion {
	HTTP10("HTTP/1.0"), HTTP11("HTTP/1.1"), HTTP2("HTTP/2.0");

	private final static Map<Integer, String> responses = new HashMap<>();
	static {
		responses.put(100, "Continue");
		responses.put(101, "Switching Protocols");
		responses.put(200, "OK");
		responses.put(201, "Created");
		responses.put(202, "Accepted");
		responses.put(202, "Non-Authoritative Information");
		responses.put(204, "No Content");
		responses.put(205, "Reset Content");
		responses.put(206, "Partial Content");
		responses.put(300, "Multiple Choices");
		responses.put(301, "Moved Permanently");
		responses.put(302, "Found");
		responses.put(303, "See Other");
		responses.put(304, "Not Modified");
		responses.put(305, "Use Proxy");
		responses.put(307, "Temporary Redirect");
		responses.put(400, "Bad Request");
		responses.put(401, "Unauthorized");
		responses.put(402, "Payment Required");
		responses.put(403, "Frobidden");
		responses.put(404, "Not Found");
		responses.put(405, "Method Not Allowed");
		responses.put(406, "Not Acceptable");
		responses.put(407, "Proxy Authentication Required");
		responses.put(408, "Request Timeout");
		responses.put(409, "Conflict");
		responses.put(410, "Gone");
		responses.put(411, "Length Required");
		responses.put(412, "Precondition Failed");
		responses.put(413, "Payload Too Large");
		responses.put(414, "URI Too Long");
		responses.put(415, "Unsupported Media Type");
		responses.put(417, "Expectation Failed");
		responses.put(421, "Misdirected Request");
		responses.put(426, "Upgrade Required");
		responses.put(431, "Request Header Fields Too Large");
		responses.put(500, "Internal Server Error");
		responses.put(501, "Not Implemented");
		responses.put(502, "Bad Gateway");
		responses.put(503, "Service Unavailable");
		responses.put(504, "Gateway Timeout");
		responses.put(505, "HTTP Version Not Supported");
	}
	private final String name;

	HttpVersion(String name) {
		this.name = name;
	}

	static HttpVersion parse(String version) {
		switch (version) {
		case "HTTP/1.1":
			return HTTP11;
		case "HTTP/2.0":
			return HTTP2;
		default:
			return HTTP10;
		}
	}

	public String statusLine(int code) {
		return this != HTTP2 ? name + " " + code + " " + responses.getOrDefault(code, "Unknown Reason phrase") + "\r\n"
				: "";
	}

	@Override
	public String toString() {
		return name;
	}
}
