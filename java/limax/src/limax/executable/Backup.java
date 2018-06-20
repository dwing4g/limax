package limax.executable;

class Backup extends JmxTool {

	@Override
	public void build(Options options) {
		options.add(Jmxc.options());
		options.add(new String[] { "-d", "destination", "backup to destination directory", null });
		options.add(new String[] { "-i", "increment", "backup increment", "false" });
	}

	@Override
	public void run(Options options) throws Exception {
		backup(Jmxc.connect(options), options.getValue("-d"), Boolean.parseBoolean(options.getValue("-i")));
	}

	public static void backup(Jmxc jmxc, String dest, boolean increment) throws Exception {
		try {
			jmxc.invoke("limax.zdb:type=Zdb,name=Zdb", "backup", new Object[] { dest, increment },
					new String[] { String.class.getName(), boolean.class.getName() });
		} catch (Throwable e) {
		} finally {
			jmxc.close();
		}
	}
}
