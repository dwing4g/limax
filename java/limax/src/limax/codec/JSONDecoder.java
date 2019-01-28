package limax.codec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JSONDecoder implements CharConsumer {
	private final JSONConsumer consumer;
	private final JSONValue root = new JSONValue() {
		@Override
		public boolean accept(char c) {
			if (Character.isWhitespace(c))
				return true;
			if (json != null)
				throw new RuntimeException("json has been parsed.");
			return false;
		}

		@Override
		public void reduce(Object v) {
			if (consumer != null)
				consumer.accept(new JSON(v));
			else
				json = new JSON(v);
		}
	};

	private JSONValue current = root;
	private JSON json;

	public JSONDecoder(JSONConsumer consumer) {
		this.consumer = consumer;
	}

	public JSONDecoder() {
		this(null);
	}

	private interface JSONValue {
		boolean accept(char c);

		void reduce(Object v);
	}

	private class JSONObject implements JSONValue {
		private final JSONValue parent = current;
		private final Map<String, Object> map = new HashMap<String, Object>();
		private String key;
		private int stage = 0;

		@Override
		public boolean accept(char c) {
			switch (stage) {
			case 0:
				stage = 1;
				return true;
			case 1:
				if (Character.isWhitespace(c))
					return true;
				if (c == '}') {
					(current = parent).reduce(map);
					return true;
				}
				return false;
			case 2:
				if (Character.isWhitespace(c))
					return true;
				if (c == ':' || c == '=') {
					stage = 3;
					return true;
				}
				throw new RuntimeException("Object expect [:=] but encounter <" + c + ">");
			case 4:
				if (Character.isWhitespace(c))
					return true;
				if (c == ',' || c == ';') {
					stage = 1;
					return true;
				}
				if (c == '}') {
					(current = parent).reduce(map);
					return true;
				}
				throw new RuntimeException("Object expect [,;}] but encounter <" + c + ">");
			}
			return Character.isWhitespace(c);
		}

		@Override
		public void reduce(Object v) {
			if (stage == 1) {
				key = (String) v;
				stage = 2;
			} else {
				map.put(key, v);
				stage = 4;
			}
		}
	}

	private class JSONArray implements JSONValue {
		private final JSONValue parent = current;
		private final List<Object> list = new ArrayList<Object>();
		private int stage = 0;

		@Override
		public boolean accept(char c) {
			switch (stage) {
			case 0:
				stage = 1;
				return true;
			case 1:
				if (Character.isWhitespace(c))
					return true;
				if (c == ']') {
					(current = parent).reduce(list);
					return true;
				}
				return false;
			default:
				if (Character.isWhitespace(c))
					return true;
				if (c == ',' || c == ';') {
					stage = 1;
					return true;
				}
				if (c == ']') {
					(current = parent).reduce(list);
					return true;
				}
				throw new RuntimeException("List expect [,;]] but encounter <" + c + ">");
			}
		}

		@Override
		public void reduce(Object v) {
			list.add(v);
			stage = 2;
		}
	}

	private class JSONString implements JSONValue {
		private final JSONValue parent = current;
		private final StringBuilder sb = new StringBuilder();
		private int stage;

		@Override
		public boolean accept(char c) {
			if (stage < 0) {
				int ch = Character.digit(c, 16);
				if (ch == -1)
					throw new RuntimeException("bad hex char <" + c + ">");
				stage = (stage << 4) | ch;
				if ((stage & 0xffff0000) == 0xfff00000) {
					sb.append((char) stage);
					stage = 0x40000000;
				}
			} else if ((stage & 0x20000000) != 0) {
				switch (c) {
				case '"':
				case '\\':
				case '/':
					sb.append(c);
					break;
				case 'b':
					sb.append('\b');
					break;
				case 'f':
					sb.append('\f');
					break;
				case 'n':
					sb.append('\n');
					break;
				case 'r':
					sb.append('\r');
					break;
				case 't':
					sb.append('\t');
					break;
				case 'u':
					stage = 0xfffffff0;
					break;
				default:
					throw new RuntimeException("unsupported escape character <" + c + ">");
				}
				stage &= ~0x20000000;
			} else if (c == '"') {
				if ((stage & 0x40000000) != 0)
					(current = parent).reduce(sb.toString());
				stage |= 0x40000000;
			} else if (c == '\\')
				stage |= 0x20000000;
			else
				sb.append(c);
			return true;
		}

		@Override
		public void reduce(Object v) {
		}
	}

	private class JSONNumber implements JSONValue {
		private final JSONValue parent = current;
		private final StringBuilder sb = new StringBuilder();
		private final static String chars = "+-0123456789Ee.";
		private boolean isDouble = false;

		@Override
		public boolean accept(char c) {
			if (chars.indexOf(c) == -1) {
				current = parent;
				if (isDouble)
					parent.reduce(Double.parseDouble(sb.toString()));
				else
					parent.reduce(Long.parseLong(sb.toString()));
				return parent.accept(c);
			}
			if (c == '.')
				isDouble = true;
			sb.append(c);
			return true;
		}

		@Override
		public void reduce(Object v) {
		}
	}

	private class JSONConst implements JSONValue {
		private final JSONValue parent = current;
		private final String match;
		private final Object value;
		private int stage = 0;

		JSONConst(String accept, Object match) {
			this.match = accept;
			this.value = match;
		}

		@Override
		public boolean accept(char c) {
			if (Character.toLowerCase(c) != match.charAt(stage++))
				throw new RuntimeException("for const <" + match + "> encounter unexpected <" + c + ">");
			if (stage == match.length())
				(current = parent).reduce(value);
			return true;
		}

		@Override
		public void reduce(Object v) {
		}
	}

	public void accept(char c) {
		while (!current.accept(c))
			switch (c) {
			case '{':
				current = new JSONObject();
				break;
			case '[':
				current = new JSONArray();
				break;
			case '"':
				current = new JSONString();
				break;
			case '-':
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
				current = new JSONNumber();
				break;
			case 't':
			case 'T':
				current = new JSONConst("true", JSON.True);
				break;
			case 'f':
			case 'F':
				current = new JSONConst("false", JSON.False);
				break;
			case 'n':
			case 'N':
				current = new JSONConst("null", JSON.Null);
				break;
			default:
				throw new RuntimeException("unknown character <" + c + "> current = " + current);
			}
	}

	void flush() {
		accept(' ');
	}

	public JSON get() {
		return json;
	}
}
