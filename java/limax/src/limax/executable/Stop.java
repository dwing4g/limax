package limax.executable;

class Stop extends JmxTool {

	@Override
	public void build(Options options) {
		options.add(Jmxc.options());
		options.add(new String[] { "-d", "milliseconds", "delay milliseconds then stop", "0" });
	}

	@Override
	public void run(Options options) throws Exception {
		stop(Jmxc.connect(options), Long.parseLong(options.getValue("-d")));
	}

	public static void stop(Jmxc jmxc, long delay) throws Exception {
		try {
			jmxc.setAttribute("limax.xmlconfig:type=Service,name=Stopper", "StopTime", delay);
		} catch (Throwable e) {
		} finally {
			jmxc.close();
		}
	}
}
