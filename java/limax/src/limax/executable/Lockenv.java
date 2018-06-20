package limax.executable;

class Lockenv extends JmxTool {
	@Override
	void build(Options options) {
		options.add(Jmxc.options());
	}

	@Override
	void run(Options options) throws Exception {
		Jmxc jmxc = Jmxc.connect(options);
		try {
			System.out.println(jmxc.invoke("limax.util:type=LockEnvironment", "dump", null, null));
		} finally {
			jmxc.close();
		}
	}

}
