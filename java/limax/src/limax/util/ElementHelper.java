package limax.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public final class ElementHelper {
	private Element self;
	private final Map<String, String> attrs = new HashMap<>();

	private static Properties properties;

	public static void setProperties(Properties proper) {
		properties = proper;
	}

	public ElementHelper(Element ele) {
		self = ele;
		NamedNodeMap as = self.getAttributes();
		for (int i = 0; i < as.getLength(); ++i) {
			Attr attr = (Attr) as.item(i);
			attrs.put(attr.getName(), attr.getValue());
		}
	}

	public String getTag() {
		return self.getTagName();
	}

	public String getString(String name) {
		String value = attrs.remove(name);
		if (value == null)
			value = "";
		if (value.length() > 2 && value.startsWith("$") && value.endsWith("$")) {
			value = value.substring(1, value.length() - 1);
			String[] vs = value.split(":");
			if (vs.length > 2)
				throw new RuntimeException("bad property \"" + value + "\"");
			String key = vs[0];
			value = getProperty(key);
			if (null == value) {
				if (vs.length > 1)
					value = vs[1];
				else
					throw new RuntimeException("lost property \"" + key + "\"");
			}
		}
		return value;
	}

	private String getProperty(String key) {
		String value = System.getProperty(key);
		return value != null ? value : properties != null ? properties.getProperty(key) : null;
	}

	public String getString(String name, String defaultvalue) {
		String value = getString(name);
		return value.isEmpty() ? defaultvalue : value;
	}

	public short getShort(String name) {
		return Short.decode(getString(name));
	}

	public short getShort(String name, short defaultvalue) {
		try {
			return getShort(name);
		} catch (NumberFormatException e) {
			return defaultvalue;
		}
	}

	public int getInt(String name) {
		return Integer.decode(getString(name));
	}

	public int getInt(String name, int defaultvalue) {
		try {
			return getInt(name);
		} catch (NumberFormatException e) {
			return defaultvalue;
		}
	}

	public long getLong(String name) {
		return Long.decode(getString(name));
	}

	public long getLong(String name, long defaultvalue) {
		try {
			return getLong(name);
		} catch (NumberFormatException e) {
			return defaultvalue;
		}
	}

	public boolean getBoolean(String name, boolean defaultvalue) {
		String value = getString(name);
		return defaultvalue ? !value.equalsIgnoreCase("false") : value.equalsIgnoreCase("true");
	}

	public byte[] getHexBytes(String name) {
		String value = getString(name);
		if (value.isEmpty())
			return null;
		try {
			return Helper.fromHexString(value);
		} catch (NumberFormatException e) {
			throw new RuntimeException(e);
		}
	}

	public Map<String, String> getUnused(String... except) {
		for (String e : except)
			attrs.remove(e);
		return attrs;
	}

	public void warnUnused(String... except) {
		if (!attrs.isEmpty() && Trace.isWarnEnabled())
			for (Map.Entry<String, String> e : getUnused(except).entrySet())
				Trace.warn(getTag() + " unused attr: " + e.getKey() + "=" + e.getValue());
	}

	public void set(String name, String value) {
		self.setAttribute(name, value);
	}

	public void set(String name, boolean value) {
		self.setAttribute(name, String.valueOf(value));
	}

	public void set(String name, int value) {
		self.setAttribute(name, String.valueOf(value));
	}

	public void set(String name, long value) {
		self.setAttribute(name, String.valueOf(value));
	}

	public void setIfEmpty(String name, String value) {
		if (self.getAttribute(name).isEmpty())
			self.setAttribute(name, value);
	}

	public void setIfEmpty(String name, boolean value) {
		setIfEmpty(name, String.valueOf(value));
	}

	public void setIfEmpty(String name, int value) {
		setIfEmpty(name, String.valueOf(value));
	}

	public void setIfEmpty(String name, long value) {
		setIfEmpty(name, String.valueOf(value));
	}

}
