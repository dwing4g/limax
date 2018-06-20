package limax.node.js.modules.http;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import limax.codec.CodecException;

public class StatusLine {
	private final static Pattern pattern = Pattern.compile("HTTP/(\\d)\\.(\\d)\\s+(\\d{3})\\s+(.+)",
			Pattern.CASE_INSENSITIVE);

	private final int majorVersion;
	private final int minorVersion;
	private final int statusCode;
	private final String statusMessage;

	StatusLine(String line) throws CodecException {
		Matcher matcher = pattern.matcher(line);
		if (!matcher.matches())
			throw new CodecException("Wrong status line " + line);
		this.majorVersion = Integer.parseInt(matcher.group(1));
		this.minorVersion = Integer.parseInt(matcher.group(2));
		this.statusCode = Integer.parseInt(matcher.group(3));
		this.statusMessage = matcher.group(4);
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

	public int getStatusCode() {
		return statusCode;
	}

	public String getStatusMessage() {
		return statusMessage;
	}
}
