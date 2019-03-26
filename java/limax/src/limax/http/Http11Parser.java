package limax.http;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import limax.http.HttpServer.Parameter;

class Http11Parser {
	private final static char CR = '\r';
	private final static char LF = '\n';

	private final int LINE_CHARACTERS_MAX;
	private final int HEADER_LINES_MAX;
	private int stage = 0;
	private StringBuilder sb = new StringBuilder();
	private HttpVersion version;
	int remain;

	private Headers headers;
	private int lines = 0;
	private Headers trailers;
	private Set<String> trailerSet;

	Http11Parser(HttpProcessor processor) {
		this.LINE_CHARACTERS_MAX = (Integer) processor.get(Parameter.HTTP11_PARSER_LINE_CHARACTERS_MAX);
		this.HEADER_LINES_MAX = (Integer) processor.get(Parameter.HTTP11_PARSER_HEADER_LINES_MAX);
	}

	private void parseRequestLine(String line) {
		String[] words = line.split("\\s+", 3);
		headers.set(":method", words[0].toUpperCase());
		headers.set(":path", URI.create(words[1]).toASCIIString());
		version = HttpVersion.parse(words[2]);
	}

	private void checkHeaderLines() {
		if (++lines >= HEADER_LINES_MAX)
			throw new RuntimeException("HEADER_LINES_MAX(" + HEADER_LINES_MAX + ")exceed.");
	}

	private void parseHeadLine(String line) {
		checkHeaderLines();
		int pos = line.indexOf(':');
		if (pos == -1)
			headers.add(line, "");
		else
			headers.add(line.substring(0, pos), line.substring(pos + 1));
	}

	private void parseLine(String line) {
		if (headers == null) {
			headers = new Headers();
			parseRequestLine(line);
		} else
			parseHeadLine(line);
	}

	private void consume(char c) {
		sb.append(c);
		if (sb.length() > LINE_CHARACTERS_MAX)
			throw new RuntimeException("LINE_CHARACTERS_MAX(" + LINE_CHARACTERS_MAX + ")exceed.");
	}

	void remainConsumed() {
		if (stage == 3) {
			end();
		} else if (stage == 6) {
			remain = 0;
			stage = 7;
		}
	}

	private void end() {
		sb = null;
		stage = -1;
		remain = -1;
	}

	void process(char c) {
		switch (stage) {
		case 0:
			if (Character.isWhitespace(c))
				break;
			stage = 1;
		case 1:
			if (c == CR)
				stage = 2;
			else
				consume(c);
			break;
		case 2:
			if (c == LF) {
				if (sb.length() == 0) {
					String info = headers.getFirst("content-length");
					if (info != null) {
						remain = Integer.parseInt(info);
						if (remain > 0)
							stage = 3;
						else if (remain == 0)
							end();
						else
							throw new RuntimeException("bad Content-Length(" + info + ")");
					} else {
						info = headers.getFirst("transfer-encoding");
						if (info != null && info.toLowerCase().contains("chunked")) {
							info = headers.getFirst("trailer");
							if (info != null) {
								trailerSet = new HashSet<>();
								int p0 = 0;
								for (int p1; (p1 = info.indexOf(p0, ',')) != -1; p0 = p1 + 1)
									trailerSet.add(info.substring(p0, p1).trim().toLowerCase());
								trailerSet.add(info.substring(p0).trim().toLowerCase());
							}
							stage = 4;
						} else {
							end();
						}
					}
				} else {
					parseLine(sb.toString());
					sb.setLength(0);
					stage = 1;
				}
			} else {
				consume(CR);
				consume(c);
				stage = 1;
			}
			break;
		case 3:
			if (remain == 0)
				end();
			break;
		case 4:
			if (c == CR)
				stage = 5;
			else
				consume(c);
			break;
		case 5:
			if (c == LF) {
				remain = 0;
				for (int i = 0, len = sb.length(); i < len; i++) {
					int digit = Character.digit(sb.charAt(i), 16);
					if (digit == -1)
						break;
					remain = (remain << 4) | digit;
				}
				sb.setLength(0);
				stage = remain > 0 ? 6 : 9;
			} else {
				consume(CR);
				consume(c);
				stage = 4;
			}
			break;
		case 6:
			if (remain == 0)
				stage = 7;
			break;
		case 7:
			if (c == CR) {
				stage = 8;
				break;
			}
			throw new RuntimeException("bad chunk");
		case 8:
			if (c == LF) {
				stage = 4;
				break;
			}
			throw new RuntimeException("bad chunk");
		case 9:
			if (c == CR)
				stage = 10;
			else
				consume(c);
			break;
		case 10:
			if (c == LF) {
				if (sb.length() == 0) {
					trailerSet = null;
					end();
				} else {
					checkHeaderLines();
					String line = sb.toString();
					int pos = line.indexOf(':');
					String key = pos == -1 ? line : line.substring(0, pos);
					if (trailerSet.contains(key.trim().toLowerCase())) {
						if (trailers == null)
							trailers = new Headers();
						trailers.add(key, pos == -1 ? "" : line.substring(pos + 1));
					}
					sb.setLength(0);
					stage = 9;
				}
			} else {
				consume(CR);
				consume(c);
				stage = 9;
			}
		}
	}

	HttpVersion getVersion() {
		return version;
	}

	Headers getHeaders() {
		return headers;
	}

	Headers getTrailers() {
		return trailers;
	}

	boolean keepalive() {
		String c = headers.getFirst("connection");
		if (c != null) {
			if (c.equalsIgnoreCase("keep-alive"))
				return true;
			if (c.equalsIgnoreCase("close"))
				return false;
		}
		return version == HttpVersion.HTTP11;
	}
}
