package limax.util;

public enum Enable {
	Default, True, False;

	public static Enable parse(String s) {
		switch (s.trim().toLowerCase()) {
		case "true":
			return True;
		case "enable":
			return True;
		case "false":
			return False;
		case "disable":
			return False;
		default:
			return Default;
		}
	}
}
