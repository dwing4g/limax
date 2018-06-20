package limax.node.js;

public interface JSONStringifiable {
	String toJSON();

	default String wrapData(String data) {
		return "{\"type\":\"" + getClass().getSimpleName() + "\",\"data\":" + data + "}";
	}
}
