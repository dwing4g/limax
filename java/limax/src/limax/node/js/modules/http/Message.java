package limax.node.js.modules.http;

import limax.codec.CodecException;

public class Message {
	private final boolean isRequest;
	private Object startLine;
	private final Header header = new Header();

	Message(boolean isRequest) {
		this.isRequest = isRequest;
	}

	void setStartLine(String line) throws CodecException {
		startLine = isRequest ? new RequestLine(line) : new StatusLine(line);
	}

	void setHeadLine(String line) {
		int pos = line.indexOf(':');
		if (pos != -1)
			header.set(line.substring(0, pos).trim(), line.substring(pos + 1).trim());
	}

	public RequestLine getRequestLine() {
		return (RequestLine) startLine;
	}

	public StatusLine getStatusLine() {
		return (StatusLine) startLine;
	}

	public Header getHeader() {
		return header;
	}

	public String getHttpVersion() {
		return isRequest ? getRequestLine().getVersion() : getStatusLine().getVersion();
	}
}
