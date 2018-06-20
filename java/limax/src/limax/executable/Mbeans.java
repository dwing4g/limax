package limax.executable;

import java.util.Set;

import javax.management.ObjectName;

class Mbeans extends JmxTool {

	@Override
	public void build(Options options) {
		options.add(Jmxc.options());
		options.add(new String[] { null, "domains ...", "domains list", null });
	}

	@Override
	public void run(Options options) throws Exception {
		Jmxc jmxc = Jmxc.connect(options);
		try {
			if (!options.hasToken())
				print("mbeans:", jmxc.queryNames(null));
			else
				for (String domain : options.getTokens())
					print("mbeans: " + domain, jmxc.queryNames(domain));
		} finally {
			jmxc.close();
		}
	}

	private void print(String prefix, Set<ObjectName> mbeans) {
		System.out.println(prefix);
		mbeans.stream().sorted().forEach(objectName -> System.out.println("	" + objectName));
	}
}
