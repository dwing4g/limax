package limax.xmlgen;

/**
 * Capacity resolve. format = [number[;[key:number[;[value:number]]]]
 */
public class CapacityConf {
	private Integer capacity = null;
	private Integer key = null;
	private Integer value = null;

	public Integer getCapacity() {
		return capacity;
	}

	public Integer getKey() {
		return key;
	}

	public CapacityConf getKeyConf() {
		return new CapacityConf(key, null, null);
	}

	public Integer getValue() {
		return value;
	}

	public CapacityConf getValueConf() {
		return new CapacityConf(value, null, null);
	}

	public void throwIf(boolean condition, String more) {
		if (condition)
			throw new IllegalArgumentException("invalid capacity!" + more);
	}

	public CapacityConf(Integer capacity, Integer key, Integer value) {
		this.capacity = capacity;
		this.key = key;
		this.value = value;
	}

	public CapacityConf(String conf) {
		for (String token : conf.split(";")) {
			if (token.trim().isEmpty())
				continue;

			String[] foreigns = token.split(":");
			for (int i = 0; i < foreigns.length; ++i)
				foreigns[i] = foreigns[i].trim();

			// set collection
			if (foreigns.length == 1) {
				throwIf(null != this.capacity || foreigns[0].isEmpty(), "[capacity] has present or isEmpty.");
				this.capacity = Integer.valueOf(foreigns[0]);
				throwIf(this.capacity < 0, "collection capacity is negative");
				continue;
			}

			// set key
			if (foreigns[0].equals("key")) {
				throwIf(null != this.key || foreigns[1].isEmpty(), "capacity.key has present or isEmpty.");
				this.key = Integer.valueOf(foreigns[1]);
				throwIf(this.key < 0, "key capacity is negative");
				continue;
			}

			// set value
			if (foreigns[0].equals("value")) {
				throwIf(null != this.value || foreigns[1].isEmpty(), "capacity.value has present or isEmpty.");
				this.value = Integer.valueOf(foreigns[1]);
				throwIf(this.value < 0, "value capacity is negative");
				continue;
			}

			throwIf(true, "unkown token.");
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		boolean first = true;

		if (null != capacity) {
			sb.append(capacity);
			if (!first)
				sb.append(";");
			first = false;
		}

		if (null != key) {
			if (!first)
				sb.append(";");
			sb.append("key:").append(key);
			first = false;
		}

		if (null != value) {
			if (!first)
				sb.append(";");
			sb.append("value:").append(value);
			first = false;
		}
		return sb.toString();
	}
}
