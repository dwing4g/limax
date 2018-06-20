package limax.zdb;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class StackTrace {
	private final String nsClass[];
	private final String nsLock[];

	public StackTrace(String nsClass, String nsLock) {
		this(nsClass.trim().split("\\s*[;,]\\s*"), nsLock.trim().split("\\s*[;,]\\s*"));
	}

	public StackTrace(String[] nsClass, String[] nsLock) {
		this.nsClass = nsClass;
		this.nsLock = nsLock;
	}

	private String findNsClass(StackTraceElement ste) {
		for (String ns : nsClass)
			if (ste.getClassName().startsWith(ns))
				return ns;
		return null;
	}

	private String findNsLock(StackTraceElement ste) {
		for (String ns : nsLock)
			if (ste.getClassName().startsWith(ns))
				return ns;
		return null;
	}

	private void increment(Map<String, AtomicInteger> counter, String key) {
		if (null != key)
			counter.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
	}

	public Map<String, AtomicInteger> top() {
		final Map<String, AtomicInteger> counter = new HashMap<>();
		final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
		for (ThreadInfo tinfo : threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds(), Integer.MAX_VALUE)) {
			if (null == tinfo)
				continue;
			increment(counter, buildLockKeyFor(tinfo));
			String namespace = null;
			int pos = 0;
			for (StackTraceElement ste : tinfo.getStackTrace()) {
				namespace = findNsClass(ste);
				if (null != namespace)
					break;
				++pos;
			}
			increment(counter, buildClassKeyFor(tinfo, namespace, pos));
		}
		return counter;
	}

	public String buildLockKeyFor(ThreadInfo tinfo) {
		if (null != tinfo.getLockInfo()) {
			for (StackTraceElement ste : tinfo.getStackTrace())
				if (null != findNsLock(ste))
					return " - " + ste.getClassName() + "." + ste.getMethodName() + " ("
							+ tinfo.getLockInfo().getClassName() + ")";
			return null;
		}
		return null;
	}

	public String buildClassKeyFor(ThreadInfo tinfo, String namespace, int pos) {
		if (null == namespace)
			return "[Others]";
		final StackTraceElement[] stes = tinfo.getStackTrace();
		final StringBuilder sb = new StringBuilder();
		sb.append(stes[pos].getClassName()).append(".").append(stes[pos].getMethodName());
		if (pos > 0)
			sb.append(" (").append(stes[0].getClassName()).append('.').append(stes[0].getMethodName()).append(")");
		return sb.toString();
	}
}
