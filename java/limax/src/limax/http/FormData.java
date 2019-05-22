package limax.http;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import limax.codec.Octets;

public class FormData {
	private static class MalformException extends RuntimeException {
		private static final long serialVersionUID = 8894837975272975561L;

		MalformException() {
			super("malformed multipart request");
		}
	}

	private final static int THRESHOLD_MIN = 8192;
	private final static int THRESHOLD_MAX = Integer.MAX_VALUE;
	private final static byte CR = 13;
	private final static byte LF = 10;
	private final static Pattern filePattern = Pattern.compile("filename\\s*=\\s*\"([^\"]*)\"",
			Pattern.CASE_INSENSITIVE);
	private final static Pattern namePattern = Pattern.compile("name\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
	private final long createTime = System.currentTimeMillis();
	private final Map<String, List<Object>> map = new HashMap<>();
	private Octets raw = new Octets();
	private boolean urlencoded;
	private MultipartParser parser;
	private long postLimit;
	private long amount = 0;

	private void merge(String key, Object value) {
		map.computeIfAbsent(key, _key -> new ArrayList<>()).add(value);
	}

	private void merge(String line) {
		int pos = line.indexOf("=");
		try {
			if (pos == -1)
				merge(URLDecoder.decode(line, "utf8"), "");
			else
				merge(URLDecoder.decode(line.substring(0, pos), "utf8"),
						URLDecoder.decode(line.substring(pos + 1), "utf8"));
		} catch (UnsupportedEncodingException e) {
		}
	}

	private void decode(String rawquery) {
		for (String line : rawquery.split("&"))
			merge(line);
	}

	FormData(URI uri) {
		if (uri != null) {
			String rawquery = uri.getRawQuery();
			if (rawquery != null)
				decode(rawquery);
		}
	}

	FormData(Headers headers) {
		this.postLimit = Long.MAX_VALUE;
		String contentType = headers.getFirst("content-type").toLowerCase();
		if (contentType.startsWith("application/x-www-form-urlencoded")) {
			urlencoded = true;
		} else if (contentType.startsWith("multipart/form-data")) {
			parser = new MultipartParser();
		}
	}

	void process(byte c) {
		if (++amount > postLimit)
			throw new RuntimeException("post size exceed limit (" + postLimit + ")");
		if (parser != null)
			parser.process(c);
		else if (raw != null)
			raw.push_byte(c);
	}

	void end(boolean cancel) {
		if (parser != null) {
			parser.end(cancel);
			parser = null;
		} else if (urlencoded) {
			decode(new String(raw.array(), 0, raw.size(), StandardCharsets.UTF_8));
			urlencoded = false;
			raw = null;
		}
	}

	public Map<String, List<Object>> getData() {
		return map;
	}

	public Octets getRaw() {
		return raw;
	}

	public long getBytesCount() {
		return amount;
	}

	public long getCreateTime() {
		return createTime;
	}

	public FormData useTempFile(int threshold) {
		parser.useTempFile(threshold);
		return this;
	}

	public FormData postLimit(long postLimit) {
		this.postLimit = postLimit;
		return this;
	}

	private class MultipartParser {
		private Octets data = new Octets();
		private byte[] boundary;
		private int stage;
		private int index;
		private String name;
		private Object file;
		private int threshold = THRESHOLD_MAX;

		void useTempFile(int threshold) {
			this.threshold = Math.max(THRESHOLD_MIN, threshold);
		}

		private void flushFileChannel() {
			if (file instanceof String) {
				try {
					Path path = Files.createTempFile(null, null);
					file = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE);
					Files.delete(path);
				} catch (IOException e) {
					file = new Object();
				}
			}
			if (file instanceof FileChannel) {
				FileChannel fc = (FileChannel) file;
				try {
					fc.write(data.getByteBuffer());
				} catch (IOException e) {
					try {
						fc.close();
					} catch (IOException e1) {
					} finally {
						file = new Object();
					}
				}
			}
		}

		private void consume(byte c) {
			if (data.push_byte(c).size() >= threshold && file != null && stage > 5) {
				flushFileChannel();
				data.clear();
			}
		}

		private void begin() {
			String line = new String(data.array(), 0, data.size(), StandardCharsets.ISO_8859_1);
			data.clear();
			int pos = line.indexOf(':');
			if (pos != -1 && line.substring(0, pos).equalsIgnoreCase("Content-Disposition")) {
				Matcher matcher = namePattern.matcher(line);
				try {
					name = "__NONAME__";
					if (matcher.find())
						name = URLDecoder.decode(matcher.group(1), "utf8");
				} catch (UnsupportedEncodingException e) {
				}
				matcher = filePattern.matcher(line);
				if (matcher.find())
					file = matcher.group(1);
			}
		}

		private void commit() {
			Object value;
			if (file != null) {
				List<ByteBuffer> bbs = new ArrayList<>();
				if (file instanceof FileChannel) {
					flushFileChannel();
					if (file instanceof FileChannel) {
						FileChannel fc = (FileChannel) file;
						try {
							DataSupplier ds = DataSupplier.from(fc, 0, fc.position());
							for (ByteBuffer bb; (bb = ds.get()) != null;)
								bbs.add(bb);
						} catch (Exception e) {
						} finally {
							try {
								fc.close();
							} catch (IOException e) {
							}
						}
					}
				} else {
					bbs.add(ByteBuffer.wrap(data.getBytes()));
				}
				value = bbs;
			} else {
				value = new String(data.array(), 0, data.size(), StandardCharsets.UTF_8);
			}
			merge(name, value);
			data.clear();
			name = null;
			file = null;
		}

		private void restore(int len) {
			consume(CR);
			consume(LF);
			for (int i = 0; i < len; i++)
				consume(boundary[i]);
		}

		private void restore() {
			restore(boundary.length);
		}

		private void restore(byte c) {
			if (c == CR)
				stage = 7;
			else {
				consume(c);
				stage = 6;
			}
		}

		void process(byte c) {
			switch (stage) {
			case 0:
				if (c == CR)
					stage = 1;
				else
					consume(c);
				break;
			case 1:
				if (c != LF)
					throw new MalformException();
				boundary = data.getBytes();
				data.clear();
				stage = 2;
				break;
			case 2:
				if (c == CR)
					stage = 3;
				else
					consume(c);
				break;
			case 3:
				if (c != LF)
					throw new MalformException();
				begin();
				stage = 4;
				break;
			case 4:
				if (c == CR)
					stage = 5;
				else {
					consume(c);
					stage = 2;
				}
				break;
			case 5:
				if (c != LF)
					throw new MalformException();
				data.clear();
				stage = 6;
				break;
			case 6:
				if (c == CR)
					stage = 7;
				else
					consume(c);
				break;
			case 7:
				if (c == LF) {
					stage = 8;
					index = 0;
				} else {
					consume(CR);
					consume(c);
					stage = 6;
				}
				break;
			case 8:
				if (boundary[index] == c) {
					if (boundary.length == ++index)
						stage = 9;
				} else {
					restore(index);
					restore(c);
				}
				break;
			case 9:
				if (c == CR)
					stage = 10;
				else if (c == '-')
					stage = 11;
				else {
					restore();
					restore(c);
				}
				break;
			case 10:
				if (c != LF)
					throw new MalformException();
				commit();
				stage = 2;
				break;
			case 11:
				if (c == '-')
					stage = 12;
				else {
					restore();
					consume((byte) '-');
					restore(c);
				}
				break;
			case 12:
				if (c == CR)
					stage = 13;
				else {
					restore();
					consume((byte) '-');
					consume((byte) '-');
					restore(c);
				}
				break;
			case 13:
				if (c != LF)
					throw new MalformException();
				commit();
				stage = 14;
			}
		}

		void end(boolean cancel) {
			if (cancel)
				try {
					((FileChannel) file).close();
				} catch (Throwable t) {
				}
			else if (stage != 14)
				throw new MalformException();
		}
	}
}
