package limax.executable;

import java.util.Arrays;

class Domains extends JmxTool {

	@Override
	public void build(Options options) {
		options.add(Jmxc.options());
	}

	@Override
	public void run(Options options) throws Exception {
		Jmxc jmxc = Jmxc.connect(options);
		try {
			System.out.println();
			System.out.println("JMX Domains:");
			Arrays.stream(jmxc.mbeanServer().getDomains()).sorted()
					.forEach(domain -> System.out.println("	" + domain));
		} finally {
			jmxc.close();
		}
	}

}
