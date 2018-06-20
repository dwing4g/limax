package limax.node.js;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.script.Bindings;
import javax.script.Invocable;

import limax.util.Trace;

public final class Require implements Module {
	private final static List<Path> searchPaths = new ArrayList<>();
	private final EventLoop eventLoop;
	private final Invocable invocable;
	private final Object JSON;
	private final Bindings require;
	private final Map<String, Bindings> cache;
	private final Stack<Bindings> stack = new Stack<>();
	private final Set<Path> resolveCheck = new HashSet<>();

	static {
		String NODE_PATH = System.getenv("NODE_PATH");
		if (NODE_PATH != null)
			Arrays.stream(NODE_PATH.split(System.getProperty("path.separator"))).map(Paths::get)
					.filter(Path::isAbsolute).collect(() -> searchPaths, List::add, List::addAll);
		Path HOME = Paths.get(System.getProperty("user.home"));
		searchPaths.add(HOME.resolve(".node_modules"));
		searchPaths.add(HOME.resolve(".node_libraries"));
	}

	@SuppressWarnings("unchecked")
	Require(EventLoop eventLoop) throws Exception {
		this.eventLoop = eventLoop;
		this.invocable = eventLoop.getInvocable();
		try (Reader reader = new InputStreamReader(
				getClass().getResourceAsStream(getClass().getSimpleName() + ".js"))) {
			this.require = (Bindings) eventLoop.getInvocable().invokeMethod(eventLoop.getEngine().eval(reader), "call",
					null, this);
		}
		this.JSON = eventLoop.getEngine().get("JSON");
		this.cache = (Map<String, Bindings>) require.get("cache");
	}

	private String upper1(String s) {
		return s.substring(0, 1).toUpperCase() + s.substring(1);
	}

	private Bindings createJSObject() throws Exception {
		return (Bindings) invocable.invokeMethod(require, "__create_js_component", true);
	}

	private Bindings createJSArray() throws Exception {
		return (Bindings) invocable.invokeMethod(require, "__create_js_component", false);
	}

	private void pushJSArray(Bindings array, Object value) {
		array.put(Integer.toString(array.size()), value);
	}

	private void popJSArray(Bindings array) {
		array.remove(Integer.toString(array.size() - 1));
	}

	private interface Loader {
		Object load(Object parameters) throws Exception;

		String getName();
	}

	private Loader createFileLoader(Object callback, Path path) {
		return new Loader() {
			private final String filename = path.toString();

			@Override
			public Object load(Object parameters) throws Exception {
				return invocable.invokeMethod(callback, "call", null, path, parameters);
			}

			@Override
			public String getName() {
				return filename;
			}
		};
	}

	private Loader resolvePath(Path path, Bindings extensions) {
		if (!resolveCheck.add(path = path.normalize().toAbsolutePath()))
			throw new RuntimeException();
		String filename = path.getFileName().toString();
		if (Files.isDirectory(path)) {
			try {
				Path packageFile = path.resolve("package.json");
				if (Files.isReadable(packageFile)) {
					Loader loader = resolvePath(path.resolve((String) loadJSON(packageFile).get("main")), extensions);
					if (loader != null)
						return loader;
				}
			} catch (Exception e) {
			}
			return resolvePath(path.resolve("index"), extensions);
		}
		if (Files.isReadable(path)) {
			Optional<Object> opt = extensions.entrySet().stream().filter(e -> filename.endsWith(e.getKey()))
					.map(e -> e.getValue()).findFirst();
			if (opt.isPresent())
				return createFileLoader(opt.get(), path);
		}
		for (Map.Entry<String, Object> e : extensions.entrySet()) {
			Path test = path.resolveSibling(filename + e.getKey());
			if (Files.isReadable(test))
				return createFileLoader(e.getValue(), test);
		}
		return null;
	}

	private Loader resolveClass(String name) {
		try {
			Class<?> clazz = null;
			if (name.indexOf('.') == -1) {
				try {
					clazz = Class.forName("limax.node.js.modules." + upper1(name));
				} catch (Exception e) {
				}
			}
			if (clazz == null)
				clazz = Class.forName(name);
			Module javaModule = (Module) clazz.getConstructor(EventLoop.class).newInstance(eventLoop);
			return new Loader() {
				@Override
				public Object load(Object parameters) throws Exception {
					return loadModule(Paths.get(name), (exports, require, module, __filename, __dirname) -> javaModule
							.link(eventLoop, exports, require, module, __filename, __dirname, parameters));
				}

				@Override
				public String getName() {
					return name;
				}
			};
		} catch (Exception e) {
			return null;
		}
	}

	private interface ConsumerModuleParameter {
		void accept(Object exports, Object require, Object module, String __filename, String __dirname)
				throws Exception;
	}

	private Object loadModule(Path path, ConsumerModuleParameter consumer) throws Exception {
		String filename = path.toString();
		Object exports;
		Bindings module = cache.get(filename);
		if (module == null) {
			Bindings parent = stack.isEmpty() ? null : stack.peek();
			cache.put(filename, module = createJSObject());
			module.put("exports", exports = createJSObject());
			module.put("loaded", false);
			module.put("filename", filename);
			module.put("parent", parent);
			module.put("children", createJSArray());
			invocable.invokeMethod(require, "__set_property", module, "require", require);
			if (parent != null) {
				module.put("id", filename);
				pushJSArray((Bindings) parent.get("children"), module);
			} else {
				module.put("id", ".");
				require.put("main", module);
			}
			stack.push(module);
			try {
				Path dir = path.getParent();
				consumer.accept(exports, require, module, path.toString(), dir == null ? null : dir.toString());
				module.put("loaded", true);
			} catch (Exception e) {
				cache.remove(filename);
				if (parent != null)
					popJSArray((Bindings) parent.get("children"));
				throw e;
			} finally {
				stack.pop();
			}
		}
		return module.get("exports");
	}

	public Object loadJS(Path path, Object parameters) throws Exception {
		return loadModule(path, (exports, require, module, __filename, __dirname) -> {
			StringBuilder sb = new StringBuilder();
			sb.append("(function(exports, require, module, __filename, __dirname, parameters) {");
			sb.append(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
			sb.append("\n})");
			invocable.invokeMethod(eventLoop.getEngine().eval(sb.toString()), "call", null, exports, require, module,
					path.toString(), path.getParent().toString(), parameters);
		});
	}

	public Bindings loadJSON(Path path) throws Exception {
		return (Bindings) invocable.invokeMethod(JSON, "parse",
				new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
	}

	private String findClassName(Path path) throws Exception {
		try (JarFile jarFile = new JarFile(path.toFile())) {
			for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements();) {
				String name = e.nextElement().getName();
				if (name.endsWith(".js"))
					return name.substring(0, name.length() - 3).replace('/', '.');
			}
		}
		throw new IllegalArgumentException("Cannot find .js in JarFile " + path);
	}

	public Object loadJAR(Path path, Object parameters) throws Exception {
		return loadModule(path, (exports, require, module, __filename, __dirname) -> {
			try (URLClassLoader cl = URLClassLoader.newInstance(new URL[] { path.toUri().toURL() },
					Thread.currentThread().getContextClassLoader())) {
				((Module) cl.loadClass(findClassName(path)).getConstructor(EventLoop.class).newInstance(eventLoop))
						.link(eventLoop, exports, require, module, __filename, __dirname, parameters);
			}
		});
	}

	private Loader _resolve(String name, Bindings extensions) {
		resolveCheck.clear();
		Loader loader;
		Path path = Paths.get(name);
		if (path.isAbsolute() || name.startsWith("/")) {
			loader = resolvePath(path, extensions);
		} else if (name.startsWith("./") || name.startsWith("../")) {
			if (!stack.isEmpty())
				path = Paths.get((String) stack.peek().get("filename")).resolveSibling(name);
			loader = resolvePath(path, extensions);
		} else {
			loader = resolveClass(name);
			if (loader != null)
				return loader;
			if (!stack.isEmpty()) {
				path = Paths.get((String) stack.peek().get("filename"));
				while (loader == null && (path = path.getParent()) != null)
					if (!path.endsWith("node_modules"))
						loader = resolvePath(path.resolve("node_modules/" + name), extensions);
			}
			if (loader == null)
				loader = searchPaths.stream().map(base -> resolvePath(base.resolve(name), extensions))
						.filter(Objects::nonNull).findFirst().orElse(null);
		}
		if (loader == null)
			throw new IllegalArgumentException("Cannot find module " + name);
		return loader;
	}

	public String resolve(String name, Bindings extensions) {
		return _resolve(name, extensions).getName();
	}

	public Object require(String name, Bindings extensions, Object parameters) throws Exception {
		return _resolve(name, extensions).load(parameters);
	}

	public Object require(String name, Object[] parameters) throws Exception {
		Bindings args = createJSArray();
		if (parameters != null)
			for (Object arg : parameters)
				pushJSArray(args, arg);
		return require(name, (Bindings) require.get("extensions"), args);
	}

	public Object require(String name) throws Exception {
		return require(name, null);
	}

	public void launch(String name, Bindings extensions, Object[] args) {
		String resolved = resolve(name, extensions);
		eventLoop.execute(() -> {
			try {
				Main.launchEngine(resolved, args);
			} catch (Throwable e) {
				if (Trace.isErrorEnabled())
					Trace.error("launch module: " + name, e);
			}
		});
	}
}
