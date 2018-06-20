package limax.node.js.modules;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.script.Bindings;

import limax.node.js.Buffer;
import limax.node.js.Buffer.Data;
import limax.node.js.EventLoop;
import limax.node.js.Module;

public final class Child_process implements Module {
	private final EventLoop eventLoop;
	private int moduleMaxBuffer = 204800;
	private Object[] moduleStdio;

	public Child_process(EventLoop eventLoop) {
		this.eventLoop = eventLoop;
	}

	private enum Stdio {
		PIPE, IGNORE, INHERIT
	}

	private class Options {
		private final Bindings options;
		private String signal = null;

		Options(Bindings options) {
			this.options = options;
		}

		void signal(String signal) {
			if (this.signal == null)
				this.signal = signal;
		}

		int getMaxBuffer() {
			Object obj = options.get("maxBuffer");
			return obj instanceof Number ? ((Number) obj).intValue() : moduleMaxBuffer;
		}

		Map<String, String> getEnv() {
			return ((Bindings) options.get("env")).entrySet().stream()
					.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().toString()));
		}

		File getCwd() {
			Object obj = options.get("cwd");
			return obj instanceof String ? new File((String) obj) : null;
		}

		int getTimeout() {
			Object obj = options.get("timeout");
			return obj instanceof Number ? ((Number) obj).intValue() : 0;
		}

		boolean isKill() {
			Object obj = options.get("killSignal");
			return obj != null ? obj.equals("SIGKILL") || obj.equals(9) : false;
		}

		boolean isDetached() {
			Object obj = options.get("detached");
			return (obj instanceof Boolean) ? (Boolean) obj : false;
		}

		Data getInput() {
			Object obj = options.get("input");
			return (obj instanceof Buffer) ? ((Buffer) obj).toData()
					: obj instanceof String ? new Buffer(((String) obj).getBytes()).toData() : null;
		}

		boolean isForceStderr() {
			return options.get("__force_stderr") != null;
		}

		boolean hasStdio() {
			return options.get("stdio") != null;
		}

		Stdio[] getStdio() {
			if (isDetached())
				return new Stdio[] { Stdio.IGNORE, Stdio.IGNORE, Stdio.IGNORE };
			Object obj = options.get("stdio");
			if (obj instanceof String) {
				switch ((String) obj) {
				case "ignore":
					return new Stdio[] { Stdio.IGNORE, Stdio.IGNORE, Stdio.IGNORE };
				case "inherit":
					return new Stdio[] { Stdio.INHERIT, Stdio.INHERIT, Stdio.INHERIT };
				}
			} else if (obj instanceof Bindings) {
				Bindings bindings = (Bindings) obj;
				Stdio stdio[] = new Stdio[3];
				for (int i = 0; i < 3; i++) {
					Object std = bindings.get(Integer.toString(i));
					if (std == moduleStdio[i] || std instanceof Integer && (Integer) std == i)
						stdio[i] = Stdio.INHERIT;
					else if (std instanceof String && ((String) std).equalsIgnoreCase("ignore"))
						stdio[i] = Stdio.IGNORE;
					else
						stdio[i] = Stdio.PIPE;
				}
				return stdio;
			}
			return new Stdio[] { Stdio.PIPE, Stdio.PIPE, Stdio.PIPE };
		}
	}

	private void waitFor(java.lang.Process process, Options options) throws Exception {
		int timeout = options.getTimeout();
		if (timeout == 0)
			process.waitFor();
		else if (!process.waitFor(timeout, TimeUnit.MILLISECONDS))
			kill(process, options);
	}

	private void kill(java.lang.Process process, Options options) {
		if (process.isAlive())
			if (options.isKill()) {
				process.destroyForcibly();
				options.signal("SIGKILL");
			} else {
				process.destroy();
				options.signal("SIGTERM");
			}
	}

	public class ChildProcess {
		private final Options options;
		private final int maxBuffer;
		private final Stdio[] stdio;
		private ByteArrayOutputStream baosStdout = new ByteArrayOutputStream();
		private ByteArrayOutputStream baosStderr = new ByteArrayOutputStream();
		private final CountDownLatch cdl;
		private java.lang.Process process;
		private InputStream inputStream;
		private InputStream errorStream;
		private OutputStream outputStream;

		ChildProcess(Object[] command, Options options, Object callback) {
			this.options = options;
			this.maxBuffer = options.getMaxBuffer();
			this.stdio = options.getStdio();
			int nreader = 0;
			if (stdio[1] == Stdio.PIPE)
				nreader++;
			if (stdio[2] == Stdio.PIPE)
				nreader++;
			this.cdl = new CountDownLatch(nreader);
			eventLoop.execute(callback, r -> {
				ProcessBuilder pb = new ProcessBuilder(
						Arrays.stream(command).map(o -> (String) o).toArray(String[]::new));
				pb.environment().clear();
				pb.environment().putAll(options.getEnv());
				pb.directory(options.getCwd());
				if (stdio[0] == Stdio.INHERIT)
					pb.redirectInput(Redirect.INHERIT);
				if (stdio[1] == Stdio.INHERIT)
					pb.redirectOutput(Redirect.INHERIT);
				if (stdio[2] == Stdio.INHERIT)
					pb.redirectError(Redirect.INHERIT);
				process = pb.start();
				if (stdio[0] == Stdio.PIPE)
					inputStream = process.getInputStream();
				else if (stdio[0] == Stdio.IGNORE)
					process.getInputStream().close();
				if (stdio[1] == Stdio.PIPE)
					outputStream = process.getOutputStream();
				else if (stdio[1] == Stdio.IGNORE)
					process.getOutputStream().close();
				if (stdio[2] == Stdio.PIPE)
					errorStream = process.getErrorStream();
				else if (stdio[2] == Stdio.IGNORE)
					process.getErrorStream().close();
				if (options.isDetached())
					return;
				waitFor(process, options);
				cdl.await();
				Map<String, Object> map = new HashMap<>();
				map.put("status", process.exitValue());
				map.put("stdout", new Buffer(baosStdout.toByteArray()));
				map.put("stderr", new Buffer(baosStderr.toByteArray()));
				map.put("signal", options.signal);
				r.add(map);
			});
		}

		public void IN(boolean outerr, Object callback) {
			ByteArrayOutputStream os = outerr ? baosStdout : baosStderr;
			@SuppressWarnings("resource")
			InputStream is = outerr ? inputStream : errorStream;
			eventLoop.execute(callback, r -> {
				byte[] data = new byte[16384];
				int nread = is.read(data);
				if (nread > 0) {
					os.write(data, 0, nread);
					r.add(new Buffer(data, 0, nread));
					if (os.size() > maxBuffer)
						Child_process.this.kill(process, options);
				} else
					cdl.countDown();
			});
		}

		public void OUT(Buffer buffer, Object callback) {
			eventLoop.execute(callback, r -> {
				Data data = buffer.toData();
				outputStream.write(data.buf, data.off, data.len);
			});
		}

		public boolean isPipe(int i) {
			return stdio[i] == Stdio.PIPE;
		}

		public void kill(Object name) {
			if (process.isAlive())
				if (name.equals("SIGKILL") || name.equals(9)) {
					process.destroyForcibly();
					options.signal("SIGKILL");
				} else {
					process.destroy();
					options.signal("SIGTERM");
				}
		}
	}

	public ChildProcess exec(Object[] command, Bindings options, Object callback) {
		return new ChildProcess(command, new Options(options), callback);
	}

	public Object sync(Object[] command, Bindings _options) {
		ByteArrayOutputStream baosStdout = new ByteArrayOutputStream();
		ByteArrayOutputStream baosStderr = new ByteArrayOutputStream();
		Exception exc = null;
		int status = 0;
		Options options = new Options(_options);
		try {
			ProcessBuilder pb = new ProcessBuilder(Arrays.stream(command).map(o -> (String) o).toArray(String[]::new));
			pb.environment().clear();
			pb.environment().putAll(options.getEnv());
			pb.directory(options.getCwd());
			Data input = options.getInput();
			Stdio[] stdio = options.getStdio();
			if (input == null) {
				if (stdio[0] != Stdio.IGNORE)
					pb.redirectInput(Redirect.INHERIT);
			}
			boolean errorPipe = options.isForceStderr() ? options.hasStdio() ? stdio[2] == Stdio.INHERIT : true : false;
			java.lang.Process process = pb.start();
			if (input != null)
				try (OutputStream os = process.getOutputStream()) {
					os.write(input.buf, input.off, input.len);
				}
			else if (stdio[0] == Stdio.IGNORE)
				process.getOutputStream().close();
			int maxBuffer = options.getMaxBuffer();
			Future<?> future1 = eventLoop.submit(() -> {
				try {
					InputStream os = process.getInputStream();
					byte[] data = new byte[16384];
					for (int nread, total = 0; (nread = os.read(data)) != -1;) {
						if ((total += nread) > maxBuffer) {
							kill(process, options);
							return;
						}
						baosStdout.write(data, 0, nread);
					}
				} catch (Exception e) {
				}
			});
			Future<?> future2 = eventLoop.submit(() -> {
				try {
					InputStream os = process.getErrorStream();
					byte[] data = new byte[16384];
					for (int nread, total = 0; (nread = os.read(data)) != -1;) {
						if ((total += nread) > maxBuffer) {
							kill(process, options);
							return;
						}
						baosStderr.write(data, 0, nread);
						if (errorPipe)
							System.err.write(data, 0, nread);
					}
				} catch (Exception e) {
				}
			});
			waitFor(process, options);
			future1.get();
			future2.get();
			status = process.exitValue();
		} catch (Exception e) {
			exc = e;
		}
		Map<String, Object> map = new HashMap<>();
		map.put("error", exc);
		map.put("status", status);
		map.put("stdout", new Buffer(baosStdout.toByteArray()));
		map.put("stderr", new Buffer(baosStderr.toByteArray()));
		map.put("signal", options.signal);
		return map;
	}

	public int getMaxBuffer() {
		return moduleMaxBuffer;
	}

	public void setMaxBuffer(Object val) {
		if (val instanceof Number)
			moduleMaxBuffer = ((Number) val).intValue();
	}

	public void setProcess(Bindings process) {
		moduleStdio = new Object[] { process.get("stdin"), process.get("stderr"), process.get("stdout") };
	}
}
