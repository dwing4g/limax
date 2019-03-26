package limax.executable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

class Top extends JmxTool {
	private final static String[][] _options_ = {
			{ "-sc", "nsClass", "namespace for class. order is meaning. e.g. \"net.;zdb.\"", "" },
			{ "-sl", "nsLock", "namespace for lock. order is meaning. e.g. \"net.;zdb.\"", "" },
			{ "-r", "period", "period(milliseconds) of top.", "1500" }, { "-n", "limit", "print lines number.", "10" },
			{ "-o", null, "print [Others].", null }, };

	@Override
	public void build(Options options) {
		options.add(Jmxc.options());
		options.add(_options_);
	}

	public static boolean isLockEntry(Entry<String, AtomicInteger> entry) {
		return entry.getKey().startsWith(" - ");
	}

	private static class OrderBy implements Comparator<Entry<String, AtomicInteger>> {
		OrderBy() {
		}

		@Override
		public int compare(Entry<String, AtomicInteger> o1, Entry<String, AtomicInteger> o2) {
			int magic = 0;
			magic |= isLockEntry(o1) ? 1 : 0;
			magic |= isLockEntry(o2) ? 2 : 0;
			switch (magic) {
			case 1:
				return -1; // o1 is lock, o2 is not.
			case 2:
				return +1; // o2 is lock, o1 is not.
			case 3:
			case 0:
				break; // both are lock or not.
			}
			// bigger first
			int c = o2.getValue().get() - o1.getValue().get();
			if (c != 0)
				return c;
			return o2.getKey().compareTo(o1.getKey());
		}

		@Override
		public boolean equals(Object obj) {
			return super.equals(obj);
		}
	}

	@Override
	public void run(Options options) throws Exception {
		final String nsClass = options.getValue("-sc");
		final String nsLock = options.getValue("-sl");
		final int period = Integer.parseInt(options.getValue("-r"));
		int limit = Integer.parseInt(options.getValue("-n"));
		if (limit < 0)
			limit = Integer.MAX_VALUE;
		final boolean others = options.getValue("-o") != null;
		Jmxc jmxc = Jmxc.connect(options);
		try {
			while (true) {
				@SuppressWarnings("unchecked")
				Entry<String, AtomicInteger>[] result = top(jmxc, nsClass, nsLock).entrySet().toArray(new Entry[0]);
				Arrays.sort(result, new OrderBy());
				int limitLock = 0;
				int limitClass = 0;
				for (Entry<String, AtomicInteger> entry : result) {
					if (isLockEntry(entry)) {
						if (limitLock < limit) {
							if (limitLock == 0)
								System.out.println();
							++limitLock;
							System.out.format("%-4s%s\n", entry.getValue().get(), entry.getKey());
						}
						if (limitLock == limit) {
							++limitLock;
							System.out.println(" - ...");
						}
					} else if (!entry.getKey().equals("[Others]") || others) {
						if (limitClass < limit) {
							if (limitClass == 0)
								System.out.println();
							++limitClass;
							System.out.format("%-5s%s\n", entry.getValue(), entry.getKey());
						}
						if (limitClass == limit) {
							++limitClass;
							System.out.println("...");
						}
					}
				}
				System.out.println("------------------------------------");
				Thread.sleep(period);
			}
		} finally {
			jmxc.close();
		}
	}

	final static String[] signature = new String[] { "java.lang.String", "java.lang.String" };

	@SuppressWarnings("unchecked")
	public static Map<String, AtomicInteger> top(Jmxc jmxc, String nsClass, String nsLock) throws Exception {
		return (Map<String, AtomicInteger>) jmxc.invoke("limax.zdb:type=Zdb,name=Zdb", "top",
				new Object[] { nsClass, nsLock }, signature);
	}
}
