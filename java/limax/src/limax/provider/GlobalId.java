package limax.provider;

import limax.provider.globalid.GlobalIdListener;
import limax.providerglobalid.GroupName;

public final class GlobalId {
	private static long timeout = 2000;

	private GlobalId() {
	}

	public static class Exception extends RuntimeException {
		private static final long serialVersionUID = -4431315946137702906L;

		public Exception(Throwable t) {
			super(t);
		}

		public Exception(String s) {
			super(s);
		}
	}

	public static boolean create(String group, String name) {
		return GlobalIdListener.create(new GroupName(group, name));
	}

	public static boolean delete(String group, String name) {
		return GlobalIdListener.delete(new GroupName(group, name));
	}

	public static boolean exist(String group, String name) {
		return GlobalIdListener.exist(new GroupName(group, name));
	}

	public static Long allocate(String group) {
		return GlobalIdListener.requestId(group);
	}

	public static void runOnValidation(Runnable r) {
		GlobalIdListener.runOnValidation(r);
	}

	public static void setTimeout(long timeout) {
		GlobalId.timeout = timeout;
	}

	public static long getTimeout() {
		return timeout;
	}
}