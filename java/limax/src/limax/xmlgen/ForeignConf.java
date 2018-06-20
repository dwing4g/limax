package limax.xmlgen;

/**
 * Foreign parse. format = [[key:tableName][;[[value:]tableName]]]
 */
public class ForeignConf {
	private String key = null;
	private String value = null;

	public String getKey() {
		return key;
	}

	public String getValue() {
		return value;
	}

	public void throwIf(boolean condition, String more) {
		if (condition)
			throw new IllegalArgumentException("invalid conf!" + more);
	}

	public ForeignConf(String key, String value) {
		this.key = key;
		this.value = value;
	}

	public ForeignConf(String conf) {
		for (String keyValue : conf.split(";")) {
			if (keyValue.trim().isEmpty())
				continue;

			String[] foreigns = keyValue.split(":");
			for (int i = 0; i < foreigns.length; ++i)
				foreigns[i] = foreigns[i].trim();

			// set value no named.
			if (foreigns.length == 1) {
				throwIf(null != this.value || foreigns[0].isEmpty(), "[value] has present or isEmpty.");
				this.value = foreigns[0];
				continue;
			}

			// set key
			if (foreigns[0].equals("key")) {
				throwIf(null != this.key || foreigns[1].isEmpty(), "key isEmpty.");
				this.key = foreigns[1];
				continue;
			}

			// set value
			if (foreigns[0].equals("value")) {
				throwIf(null != this.value || foreigns[1].isEmpty(), "value isEmpty.");
				this.value = foreigns[1];
				continue;
			}

			throwIf(true, "unkown token.");
		}
		// throwIf(null != key && null == value,
		// "unsupported. has key but no value.");
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (null != key)
			sb.append("key:").append(key);
		if (null != value) {
			if (null != key)
				sb.append(";");
			sb.append("value:").append(value);
		}
		return sb.toString();
	}
}
