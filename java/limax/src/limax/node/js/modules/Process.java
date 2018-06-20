package limax.node.js.modules;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import limax.node.js.Buffer;
import limax.node.js.EventLoop;
import limax.node.js.Module;

public final class Process implements Module {
	private final EventLoop eventLoop;
	private static Path cwd;

	static {
		cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
	}

	public Process(EventLoop eventLoop) throws Exception {
		this.eventLoop = eventLoop;
		this.platform = System.getProperty("os.name");
	}

	public void IN(Object callback) {
		eventLoop.execute(callback, r -> {
			byte[] data = new byte[4096];
			int nread = System.in.read(data);
			if (nread > 0)
				r.add(new Buffer(data, 0, nread));
		});
	}

	public void OUT(boolean outerr, String chunk, Object callback) {
		eventLoop.execute(callback, r -> (outerr ? System.out : System.err).print(chunk));
	}

	public void chdir(String directory) {
		Path dst = Paths.get(directory);
		if (!dst.isAbsolute())
			dst = cwd.resolve(dst).normalize().toAbsolutePath();
		if (!Files.isDirectory(dst))
			throw new IllegalArgumentException(directory + " is illegal directory.");
		cwd = dst;
	}

	public String cwd() {
		return cwd.toString();
	}

	public static Path currentWorkingDirectory() {
		return cwd;
	}

	public int[] hrtime(Object[] time) {
		int[] r = new int[2];
		long t = System.nanoTime();
		if (time != null)
			t -= (int) time[0] * 1000000000L + (int) time[1];
		r[0] = (int) (t / 1000000000L);
		r[1] = (int) (t % 1000000000L);
		return r;
	}

	public Map<String, String> memoryUsage() {
		Map<String, String> map = new HashMap<>();
		MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
		MemoryUsage mu = mbean.getHeapMemoryUsage();
		map.put("heapInit", String.valueOf(mu.getInit()));
		map.put("heapUsed", String.valueOf(mu.getUsed()));
		map.put("heapCommitted", String.valueOf(mu.getCommitted()));
		map.put("heapMax", String.valueOf(mu.getMax()));
		mu = mbean.getNonHeapMemoryUsage();
		map.put("nonHeapInit", String.valueOf(mu.getInit()));
		map.put("nonHeapUsed", String.valueOf(mu.getUsed()));
		map.put("nonHeapCommitted", String.valueOf(mu.getCommitted()));
		map.put("nonHeapMax", String.valueOf(mu.getMax()));
		return map;
	}

	public final String platform;
}
