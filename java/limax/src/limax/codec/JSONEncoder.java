package limax.codec;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class JSONEncoder {
	private JSONEncoder() {
	}

	@FunctionalInterface
	private interface Action {
		void apply(Set<Object> l, Object v, Appendable a) throws Exception;
	}

	private final static Map<Class<?>, Action> actions = new ConcurrentHashMap<>();
	private final static Action stringAction = (l, v, a) -> {
		a.append('"');
		String s = v.toString();
		for (int i = 0, n = s.length(); i < n; i++) {
			char c = s.charAt(i);
			switch (c) {
			case '"':
				a.append("\\\"");
				break;
			case '\\':
				a.append("\\\\");
				break;
			case '\b':
				a.append("\\b");
				break;
			case '\f':
				a.append("\\f");
				break;
			case '\n':
				a.append("\\n");
				break;
			case '\r':
				a.append("\\r");
				break;
			case '\t':
				a.append("\\t");
				break;
			default:
				if (c < ' ')
					a.append(String.format("\\u%04x", (int) c));
				else
					a.append(c);
			}
		}
		a.append('"');
	};
	private final static Action arrayAction = (l, v, a) -> {
		String comma = "";
		a.append('[');
		for (int i = 0, n = Array.getLength(v); i < n; i++) {
			a.append(comma);
			encode(l, Array.get(v, i), a);
			comma = ",";
		}
		a.append(']');
	};
	private final static Action collectionAction = (l, v, a) -> {
		String comma = "";
		a.append('[');
		for (Object i : (Collection<?>) v) {
			a.append(comma);
			encode(l, i, a);
			comma = ",";
		}
		a.append(']');
	};
	private final static Action mapAction = (l, v, a) -> {
		String comma = "";
		a.append('{');
		for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
			a.append(comma);
			stringAction.apply(l, e.getKey(), a);
			a.append(":");
			encode(l, e.getValue(), a);
			comma = ",";
		}
		a.append('}');
	};

	static {
		Action numberAction = (l, v, a) -> a.append(v.toString());
		Action booleanAction = (l, v, a) -> a.append(Boolean.toString((boolean) v));
		actions.put(byte.class, numberAction);
		actions.put(short.class, numberAction);
		actions.put(int.class, numberAction);
		actions.put(long.class, numberAction);
		actions.put(float.class, numberAction);
		actions.put(double.class, numberAction);
		actions.put(Byte.class, numberAction);
		actions.put(Short.class, numberAction);
		actions.put(Integer.class, numberAction);
		actions.put(Long.class, numberAction);
		actions.put(Float.class, numberAction);
		actions.put(Double.class, numberAction);
		actions.put(AtomicInteger.class, numberAction);
		actions.put(AtomicLong.class, numberAction);
		actions.put(boolean.class, booleanAction);
		actions.put(Boolean.class, booleanAction);
		actions.put(AtomicBoolean.class, booleanAction);
		actions.put(char.class, stringAction);
		actions.put(Character.class, stringAction);
		actions.put(String.class, stringAction);
		actions.put(JSON.class, (l, v, a) -> {
			Object o = ((JSON) v).data;
			if (o == null)
				throw new JSONException("JSONEncoder encounter undefined JSON Object.");
			encode(l, o, a);
		});
		actions.put(JSON.Null.class, (l, v, a) -> encode(l, null, a));
		actions.put(JSON.True.class, (l, v, a) -> encode(l, true, a));
		actions.put(JSON.False.class, (l, v, a) -> encode(l, false, a));
	}

	private static Action makeFieldAction(Field field) {
		field.setAccessible(true);
		return (l, v, a) -> {
			stringAction.apply(l, field.getName(), a);
			a.append(':');
			encode(l, field.get(v), a);
		};
	}

	private static Action packFieldActions(Action[] actions) {
		return (l, v, a) -> {
			String comma = "";
			a.append('{');
			for (Action action : actions) {
				a.append(comma);
				action.apply(l, v, a);
				comma = ",";
			}
			a.append('}');
		};
	}

	static void encode(Set<Object> l, Object v, Appendable a) throws Exception {
		if (v instanceof JSONSerializable) {
			Class<?> c = v.getClass();
			if (!l.add(v))
				throw new JSONException("JSONEncoder loop detected. object = " + v + ", type = " + c.getName());
			if (v instanceof JSONMarshal) {
				JSONBuilder jb = new JSONBuilder(l);
				((JSONMarshal) v).marshal(jb);
				a.append(jb.toString());
			} else
				actions.computeIfAbsent(c,
						k -> packFieldActions(Arrays.stream(c.getDeclaredFields())
								.filter(field -> (field.getModifiers() & (Modifier.TRANSIENT | Modifier.STATIC)) == 0)
								.map(field -> makeFieldAction(field)).toArray(Action[]::new)))
						.apply(l, v, a);
			l.remove(v);
		} else if (v != null) {
			Class<?> c = v.getClass();
			Action action = actions.get(c);
			if (action == null) {
				if (c.isArray())
					action = arrayAction;
				else if (v instanceof Collection)
					action = collectionAction;
				else if (v instanceof Map)
					action = mapAction;
				else
					throw new RuntimeException("JSONEncoder encounter unrecognized type = " + c.getName());
			}
			action.apply(l, v, a);
		} else
			a.append("null");
	}

	public static void encode(Object obj, Appendable a) throws JSONException {
		try {
			encode(Collections.newSetFromMap(new IdentityHashMap<>()), obj, a);
			if (a instanceof Source)
				((Source) a).flush();
		} catch (JSONException e) {
			throw e;
		} catch (Exception e) {
			throw new JSONException(e);
		}
	}
}
