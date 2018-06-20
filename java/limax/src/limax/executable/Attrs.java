package limax.executable;

import java.util.Arrays;

import javax.management.ObjectName;

class Attrs extends JmxTool {

	@Override
	public void build(Options options) {
		final String[][] attrs = { { "-b", "bean", "name of bean. sample: limax.zdb:type=Zdb", "!" },
				{ null, "[...]", "attrs.", null }, };
		options.add(Jmxc.options());
		options.add(attrs);
	}

	@Override
	public void run(Options options) throws Exception {
		Jmxc jmxc = Jmxc.connect(options);
		try {
			ObjectName beanName = new ObjectName(options.getValue("-b"));
			jmxc.mbeanServer()
					.getAttributes(beanName,
							(options.hasToken() ? options.getTokens().toArray(new String[0])
									: Arrays.stream(jmxc.getMBeanInfo(beanName).getAttributes())
											.map(attr -> attr.getName()).toArray(n -> new String[n])))
					.asList()
					.forEach(attr -> System.out.format("%-25s%s\n", attr.getValue().toString(), attr.getName()));
		} finally {
			jmxc.close();
		}
	}
}
