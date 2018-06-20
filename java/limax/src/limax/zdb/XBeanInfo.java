package limax.zdb;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class XBeanInfo {
	private final static Map<Class<? extends XBean>, Map<String, Field>> infos = new ConcurrentHashMap<>();

	private static Map<String, Field> getFieldsMap(XBean xbean) {
		Class<? extends XBean> xbeanClass = xbean.getClass();
		return infos.computeIfAbsent(xbeanClass, v -> {
			Map<String, Field> map = new HashMap<>();
			Field[] fields = xbeanClass.getDeclaredFields();
			AccessibleObject.setAccessible(fields, true);
			for (Field field : fields)
				map.put(field.getName(), field);
			return map;
		});
	}

	static Object getValue(XBean xbean, String varname) {
		try {
			return getFieldsMap(xbean).get(varname).get(xbean);
		} catch (Exception e) {
			return null;
		}
	}

	static void setValue(XBean xbean, String varname, Object value) {
		try {
			getFieldsMap(xbean).get(varname).set(xbean, value);
		} catch (Exception e) {
		}
	}

	static Collection<Field> getFields(XBean xbean) {
		return getFieldsMap(xbean).values();
	}
}
