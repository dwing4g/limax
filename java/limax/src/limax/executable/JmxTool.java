package limax.executable;

import java.util.LinkedHashSet;
import java.util.Set;

abstract class JmxTool {

	abstract void build(Options options);

	abstract void run(Options options) throws Exception;

	JmxTool() {
	}

	private static void usage(Set<String> set) {
		System.out.println("limax jmx tool");
		System.out.println("commands:");
		set.forEach(key -> System.out.println("	" + key));
		System.exit(1);
	}

	private static String upper1(String name) {
		return name.substring(0, 1).toUpperCase() + name.substring(1);
	}

	public static void main(String[] args) throws Exception {
		Set<String> set = new LinkedHashSet<>();
		set.add("domains");
		set.add("mbeans");
		set.add("attrs");
		set.add("backup");
		set.add("top");
		set.add("stop");
		set.add("monitor");
		set.add("lockenv");

		if (args.length < 1)
			usage(set);
		String cmd = args[0].toLowerCase();
		Options options = new Options();
		try {
			JmxTool tool = (JmxTool) Class.forName("limax.executable." + upper1(cmd)).getDeclaredConstructor()
					.newInstance();
			tool.build(options);
			options.parse(args, 1);
			tool.run(options);
		} catch (Exception e) {
			System.out.println(e.getMessage());
			System.out.println("Usage: ");
			System.out.println("java -jar limax.jar jmxtool " + cmd + " [options]");
			System.out.println(options);
		}
	}
}
