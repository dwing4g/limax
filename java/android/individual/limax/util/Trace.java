package limax.util;

public enum Trace {
	DEBUG, INFO, WARN, ERROR, FATAL;

	public static boolean isDebugEnabled() {
		return logger.ordinal() <= DEBUG.ordinal();
	}

	public static boolean isInfoEnabled() {
		return logger.ordinal() <= INFO.ordinal();
	}

	public static boolean isWarnEnabled() {
		return logger.ordinal() <= WARN.ordinal();
	}

	public static boolean isErrorEnabled() {
		return logger.ordinal() <= ERROR.ordinal();
	}

	public static boolean isFatalEnabled() {
		return logger.ordinal() <= FATAL.ordinal();
	}

	public static void debug(Object message) {
		logger.trace(DEBUG, null, message);
	}

	public static void info(Object message) {
		logger.trace(INFO, null, message);
	}

	public static void warn(Object message) {
		logger.trace(WARN, null, message);
	}

	public static void error(Object message) {
		logger.trace(ERROR, null, message);
	}

	public static void fatal(Object message) {
		logger.trace(FATAL, null, message);
	}

	public static void debug(Object message, Throwable e) {
		logger.trace(DEBUG, e, message);
	}

	public static void info(Object message, Throwable e) {
		logger.trace(INFO, e, message);
	}

	public static void warn(Object message, Throwable e) {
		logger.trace(WARN, e, message);
	}

	public static void error(Object message, Throwable e) {
		logger.trace(ERROR, e, message);
	}

	public static void fatal(Object message, Throwable e) {
		logger.trace(FATAL, e, message);
	}

	public static void log(Trace level, Object message) {
		logger.trace(level, null, message);
	}

	public static void log(Trace level, Object message, Throwable e) {
		logger.trace(level, e, message);
	}

	public static void set(Trace trace) {
		logger = trace;
	}

	public static Trace get() {
		return logger;
	}

	public interface Destination {
		void printTo(String msg, Throwable e);
	}

	private final static Destination defaultPrintTo = new Destination() {
		@Override
		public void printTo(String msg, Throwable e) {
			System.out.println(msg);
			if (null != e)
				e.printStackTrace();
		}
	};

	private volatile static Trace logger = WARN;
	private volatile static Destination printTo = defaultPrintTo;

	public static void open(Destination _printTo, Trace _level) {
		if (null == _printTo)
			printTo = defaultPrintTo;
		else
			printTo = _printTo;
		logger = _level;
	}

	private String traceName(Trace t) {
		String l = t.toString();
		return (l.length() == 4) ? (l + " ") : l;
	}

	private void trace(Trace t, Throwable e, Object message) {
		if (t.ordinal() >= this.ordinal())
			printTo.printTo(traceName(t) + ' ' + '<' + Thread.currentThread().getName() + '>' + ' ' + message, e);
	}
}