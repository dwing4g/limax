package limax.executable;

import limax.util.monitor.Collector;

final class Monitor extends JmxTool {

	@Override
	void build(Options options) {
		String[][] opts = { { "-c", "className", "monitor class with implements JmxCollector", "!" } };
		options.add(opts);
	}

	@Override
	void run(Options options) throws Exception {
		limax.util.monitor.Monitor
				.start((Collector) Class.forName(options.getValue("-c")).getDeclaredConstructor().newInstance());
	}
}
