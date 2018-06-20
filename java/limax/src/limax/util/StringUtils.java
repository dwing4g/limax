package limax.util;

public final class StringUtils {
	private StringUtils() {
	}

	public static String quote(String value) {
		return "\"" + value + "\"";
	}

	public static String upper1(String value) {
		return value.substring(0, 1).toUpperCase() + value.substring(1);
	}

}
