package limax.http;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
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

	private final static byte CR = 13;
	private final static byte LF = 10;
	private final static Pattern filePattern = Pattern.compile("filename\\s*=\\s*\"([^\"]*)\"",
			Pattern.CASE_INSENSITIVE);
	private final static Pattern namePattern = Pattern.compile("name\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
	private final Map<String, Object> map = new HashMap<>();
	private MultipartParser parser;
	private Octets data;
	private long amount = 0;

	@SuppressWarnings("unchecked")
	private void merge(String key, Object value) {
		map.merge(key, value, (o, n) -> {
			if (o instanceof List) {
				((List<Object>) o).add(n);
				return o;
			} else {
				List<Object> list = new ArrayList<>();
				list.add(o);
				list.add(n);
				return list;
			}
		});
	}

	private void merge(String line) {
		int pos = line.indexOf("=");
		if (pos == -1)
			merge(line, "");
		else
			merge(line.substring(0, pos), line.substring(pos + 1));
	}

	FormData(String query) {
		if (query != null)
			for (String line : query.split("&"))
				merge(line);
	}

	FormData(boolean multipart) {
		data = new Octets();
		if (multipart)
			parser = new MultipartParser();
	}

	void process(byte c) {
		amount++;
		if (parser != null)
			parser.process(c);
		else if (c == LF) {
			merge(new String(data.array(), 0, data.size(), StandardCharsets.UTF_8));
			data.clear();
		} else if (c != CR)
			data.push_byte(c);
	}

	void end() {
		data = null;
		if (parser != null) {
			MultipartParser _parser = parser;
			parser = null;
			_parser.end();
		}
	}

	public Map<String, Object> getData() {
		return map;
	}

	public long getBytesCount() {
		return amount;
	}

	public void useTempFile(int threshold) {
		if (parser != null)
			parser.useTempFile(threshold);
	}

	private class MultipartParser {
		private byte[] boundary;
		private int stage = 0;
		private int index;
		private String name;
		private Object file;
		private int threshold = Integer.MAX_VALUE;

		void useTempFile(int threshold) {
			this.threshold = threshold;
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
			data.push_byte(c);
			if (data.size() >= threshold) {
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
				if (matcher.find()) {
					try {
						name = URLDecoder.decode(matcher.group(1), "utf8");
					} catch (UnsupportedEncodingException e) {
						name = null;
					}
				}
				matcher = filePattern.matcher(line);
				file = matcher.find() ? matcher.group(1) : null;
			}
		}

		private void commit() {
			Object value;
			if (file != null) {
				if (file instanceof FileChannel) {
					flushFileChannel();
					if (file instanceof FileChannel) {
						FileChannel fc = (FileChannel) file;
						try {
							value = fc.map(MapMode.READ_ONLY, 0, fc.position());
						} catch (IOException e) {
							value = ByteBuffer.allocate(0);
						} finally {
							try {
								fc.close();
							} catch (IOException e) {
							}
						}
					} else {
						value = ByteBuffer.allocate(0);
					}
				} else {
					value = ByteBuffer.wrap(data.getBytes());
				}
			} else {
				value = new String(data.array(), 0, data.size(), StandardCharsets.UTF_8);
			}
			if (name != null)
				merge(name, value);
			data.clear();
		}

		private void restore(int len) {
			consume(CR);
			consume(LF);
			data.append(boundary, 0, len);
		}

		private void restore() {
			restore(boundary.length);
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
					if (boundary.length == ++index) {
						stage = 9;
					}
				} else {
					restore(index);
					consume(c);
					stage = 6;
				}
				break;
			case 9:
				if (c == CR)
					stage = 10;
				else if (c == '-')
					stage = 11;
				else {
					restore();
					consume(c);
					stage = 6;
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
					consume(c);
					stage = 6;
				}
				break;
			case 12:
				if (c == CR)
					stage = 13;
				else {
					restore();
					consume((byte) '-');
					consume((byte) '-');
					consume(c);
					stage = 6;
				}
				break;
			case 13:
				if (c != LF)
					throw new MalformException();
				commit();
				stage = 14;
			}
		}

		void end() {
			try {
				((FileChannel) file).close();
			} catch (Throwable t) {
			}
			if (stage != 14)
				throw new MalformException();
		}
	}
}
