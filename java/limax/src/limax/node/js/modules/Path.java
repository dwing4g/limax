package limax.node.js.modules;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import limax.node.js.EventLoop;
import limax.node.js.Module;

public final class Path implements Module {
	public Path(EventLoop eventLoop) {
		Properties properties = System.getProperties();
		this.delimiter = properties.getProperty("path.separator");
		this.cwd = Paths.get(properties.getProperty("user.dir")).toAbsolutePath();
		this.sep = properties.getProperty("file.separator");
	}

	public String basename(String path, String ext) {
		String r = Paths.get(path).getFileName().toString();
		return ext != null && r.endsWith(ext) ? r.substring(0, r.length() - ext.length()) : r;
	}

	public final String delimiter;
	private final java.nio.file.Path cwd;
	public final String sep;

	public String dirname(String path) {
		java.nio.file.Path p0 = Paths.get(path);
		java.nio.file.Path p1 = p0.getParent();
		return p1 == null ? p0.toString() : p1.toString();
	}

	public String format(String dir, String root, String base, String name, String ext) {
		if (dir == null)
			dir = root;
		if (base == null) {
			if (name == null)
				return null;
			if (ext != null)
				base = name + ext;
		}
		return dir == null ? base : Paths.get(dir).resolve(base).toString();
	}

	public boolean isAbsolute(String path) {
		return Paths.get(path, ".").isAbsolute();
	}

	public String join(Object[] parts) {
		String[] p = new String[parts.length - 1];
		for (int i = 0; i < p.length; i++)
			p[i] = (String) parts[i + 1];
		return Paths.get((String) parts[0], p).normalize().toString();
	}

	public String normalize(String path) {
		return Paths.get(path).normalize().toString();
	}

	public Map<String, String> parse(String path) {
		Map<String, String> r = new HashMap<>();
		java.nio.file.Path p = Paths.get(path);
		r.put("root", Objects.toString(p.getRoot(), null));
		r.put("dir", Objects.toString(p.getParent(), null));
		String base = p.getFileName().toString();
		r.put("base", base);
		int pos = base.lastIndexOf('.');
		if (pos == -1) {
			r.put("name", base);
			r.put("ext", null);
		} else {
			r.put("name", base.substring(0, pos));
			r.put("ext", base.substring(pos));
		}
		return r;
	}

	public String relative(String from, String to) {
		return Paths.get(from).relativize(Paths.get(to)).toString();
	}

	public String resolve(Object[] parts) {
		java.nio.file.Path p = Paths.get((String) parts[0]);
		for (int i = 1; i < parts.length; i++)
			p = p.resolve((String) parts[i]);
		if (p.getRoot() == null)
			p = cwd.resolve(p);
		return p.toAbsolutePath().normalize().toString();
	}
}
