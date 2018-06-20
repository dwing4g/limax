package limax.xmlgen.java;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import limax.provider.ViewLifecycle;
import limax.util.Pair;
import limax.xmlgen.Bean;
import limax.xmlgen.Bind;
import limax.xmlgen.Cbean;
import limax.xmlgen.FileOperation;
import limax.xmlgen.Main;
import limax.xmlgen.NameStringToIndex;
import limax.xmlgen.Namespace;
import limax.xmlgen.Naming;
import limax.xmlgen.Service;
import limax.xmlgen.Subscribe;
import limax.xmlgen.Type;
import limax.xmlgen.Variable;
import limax.xmlgen.View;
import limax.xmlgen.Xbean;

public class Viewgen {
	final static int VIEW_MANAGER_SPLICE_LENGTH_LIMIT = 49152;
	private final File srcDir;
	private final File genDir;
	private final Namespace namespace;
	private final Service service;
	private final Collection<View> views;
	private final NameStringToIndex viewindex = new NameStringToIndex(Short.MAX_VALUE);
	private final Map<View, ViewFormatter> formatters = new IdentityHashMap<>();
	private final Map<String, Integer> dictionary = new LinkedHashMap<>();
	private int index = 0;

	public Viewgen(Service service, Namespace namespace) {
		this.service = service;
		this.namespace = namespace;
		this.genDir = new File(Main.outputPath, "gen");
		this.srcDir = new File(Main.outputPath, "src");
		this.views = namespace.getViews();
		this.viewindex.addAll(this.views);
	}

	String makeViewNameSpace(View view) {
		return service.getFullName() + "." + view.getFirstName();
	}

	int getViewIndex(View view) {
		return viewindex.getIndex(view);
	}

	String getProviderNamespace() {
		return service.getFullName() + "." + namespace.getFullName();
	}

	Collection<View> getViews() {
		return views;
	}

	private void updateName(Naming nm) {
		String name = nm.getName();
		Integer i = dictionary.get(name);
		if (i == null) {
			i = index++;
			dictionary.put(name, i);
		}
		nm.attachment(i);
	}

	private void marking() {
		Set<Type> s = new HashSet<>();
		updateName(namespace);
		views.forEach(v -> {
			for (Naming c = v.getParent(); c instanceof Namespace; c = c.getParent())
				updateName(c);
		});
		views.forEach(v -> {
			updateName(v);
			v.depends(s);
		});
		views.stream().flatMap(v -> v.getChildren().stream())
				.filter(t -> t instanceof Variable || t instanceof Bind || t instanceof Subscribe)
				.forEach(t -> updateName(t));
		views.stream().flatMap(v -> v.getChildren().stream()).filter(t -> t instanceof Bind)
				.flatMap(t -> ((Bind) t).getVariables().stream()).forEach(t -> updateName(t));
		for (Set<Type> q = new HashSet<>(); q.size() != s.size(); s.addAll(q))
			s.forEach(t -> t.depends(q));
		s.stream().filter(t -> t instanceof Bean || t instanceof Cbean || t instanceof Xbean).flatMap(t -> {
			t.attachment(true);
			if (t instanceof Bean)
				return ((Bean) t).getVariables().stream();
			if (t instanceof Cbean)
				return ((Cbean) t).getVariables().stream();
			return ((Xbean) t).getVariables().stream();
		}).forEach(t -> updateName(t));
	}

	static class Marshals {
		private class Entry {
			private final int index;
			private final Type type;

			Entry(Type type) {
				this.index = map.size();
				this.type = type;
			}
		}

		private final File genDir;
		private final String namespace;
		private final Map<String, Entry> map = new LinkedHashMap<>();

		private Marshals(File genDir, String namespace) {
			this.genDir = genDir;
			this.namespace = namespace;
		}

		private static String getTypeString(Type type) {
			return type instanceof Bean || type instanceof Xbean || type instanceof Cbean ? "E"
					: TypeName.getBoxingName(type);
		}

		private static String getTypeString(Bind bind) {
			return bind.isFullBind() ? getTypeString(bind.getValueType()) : "E";
		}

		String get(Type type) {
			map.putIfAbsent(getTypeString(type), new Entry(type));
			return "Marshals.m" + map.get(getTypeString(type)).index;
		}

		String get(Variable var) {
			return get(var.getType());
		}

		String get(Bind bind) {
			if (bind.isFullBind()) {
				Type type = bind.getValueType();
				map.putIfAbsent(getTypeString(type), new Entry(type));
			} else {
				map.putIfAbsent("E", new Entry(null));
			}
			return "Marshals.m" + map.get(getTypeString(bind)).index;
		}

		private void output(PrintStream ps) {
			ps.println("package " + namespace + ";");
			ps.println();
			if (map.containsKey("E"))
				ps.println("import limax.codec.Marshal;");
			ps.println("import limax.codec.Octets;");
			ps.println("import limax.codec.OctetsStream;");
			if (Main.scriptSupport) {
				if (map.containsKey("E"))
					ps.println("import limax.codec.StringMarshal;");
				ps.println("import limax.codec.StringStream;");
			}
			ps.println();
			ps.println("public final class Marshals {");
			map.forEach((k, v) -> {
				ps.println("	public final static" + (k.equals("E") ? " <E extends Marshal>" : "") + " Octets m"
						+ v.index + "(" + k + " value) {");
				ps.println("		OctetsStream _os_ = new OctetsStream();");
				ps.println("		if (value != null) {");
				if (v.type != null)
					Marshal.make(v.type, "value", ps, "			");
				else
					ps.println("			_os_.marshal(value);");
				ps.println("		}");
				ps.println("		return _os_;");
				ps.println("	}");
				ps.println();
				if (Main.scriptSupport) {
					ps.println("	public final static" + (k.equals("E") ? " <E extends StringMarshal>" : "")
							+ " String m" + v.index + "(String prefix, " + k + " value) {");
					ps.println(
							"		StringStream _os_ = new StringStream(ViewManager.getViewContext().getDataDictionary(), prefix);");
					ps.println("		if (value == null)");
					ps.println("			_os_.append('D');");
					ps.println("		else {");
					if (v.type != null)
						StringMarshal.make(v.type, "value", ps, "			");
					else
						ps.println("			_os_.marshal(value);");
					ps.println("		}");
					ps.println("		return _os_.toString();");
					ps.println("	}");
					ps.println();
				}
			});
			ps.println("}");
			ps.println();
		}

		private void output() {
			if (map.isEmpty())
				return;
			try (PrintStream ps = FileOperation.fopen(new File(genDir, namespace.replace(".", File.separator)),
					"Marshals.java")) {
				output(ps);
			}
		}
	}

	public void makeJavaServer() {
		if (Main.scriptSupport)
			marking();
		Marshals marshals = new Marshals(genDir, getProviderNamespace());
		views.forEach(view -> formatters.put(view, new ViewFormatter(this, view, viewindex.getIndex(view))));
		views.stream().filter(view -> view.getLifecycle() != ViewLifecycle.temporary)
				.forEach(view -> formatters.get(view).makeServer(genDir, srcDir, marshals));
		views.stream().filter(view -> view.getLifecycle() == ViewLifecycle.temporary)
				.forEach(view -> formatters.get(view).makeServer(genDir, srcDir, marshals));
		marshals.output();
		if (!views.isEmpty())
			makeServerOnly();
	}

	public ViewFormatter getViewFormatter(View view) {
		return formatters.get(view);
	}

	public void makeJavaClient() {
		Set<Type> dependTypes = new HashSet<Type>();
		views.forEach(view -> view.depends(dependTypes));
		dependTypes.stream().filter(t -> t instanceof Xbean || t instanceof Cbean)
				.forEach(t -> t.attachment(namespace));
		dependTypes.forEach(type -> {
			if (type instanceof Xbean)
				new ViewXbeanFormatter((Xbean) type).make(genDir);
			else if (type instanceof Cbean)
				new ViewCbeanFormatter((Cbean) type).make(genDir);
		});
		views.forEach(view -> formatters.put(view, new ViewFormatter(this, view, viewindex.getIndex(view))));
		views.stream().filter(view -> view.getLifecycle() != ViewLifecycle.temporary)
				.forEach(view -> formatters.get(view).makeClient(genDir, srcDir));
		views.stream().filter(view -> view.getLifecycle() == ViewLifecycle.temporary)
				.forEach(view -> formatters.get(view).makeClient(genDir, srcDir));
		if (!views.isEmpty())
			this.makeJavaClientOnly();
	}

	private Pair<List<String>, Map<String, String>> printViewStubs(String space) {
		List<String> r = new ArrayList<>();
		Map<String, String> map = new HashMap<>();
		PrintStream ps = null;
		ByteArrayOutputStream baos = null;
		int classid = 0;
		int methodid = 0;
		String classname = "";
		for (View view : views) {
			if (ps == null || baos.size() > VIEW_MANAGER_SPLICE_LENGTH_LIMIT) {
				if (ps != null) {
					ps.println("}");
					ps.println();
					map.put(classname, baos.toString());
					ps.close();
				}
				classname = "VS" + classid++;
				ps = new PrintStream(baos = new ByteArrayOutputStream());
				ps.println("package " + space + ";");
				ps.println();
				ps.println("import java.util.Collection;");
				ps.println();
				ps.println("import limax.provider.View;");
				ps.println("import limax.provider.ViewLifecycle;");
				ps.println("import limax.provider.ViewStub;");
				ps.println();
				ps.println("final class " + classname + " {");
				methodid = 0;
			}
			ps.println("	static void add" + methodid + "(Collection<ViewStub> stubs) {");
			ps.println("		stubs.add(new ViewStub() {");
			ps.println("			@Override");
			ps.println("			public short getClassIndex() {");
			ps.println("				return " + viewindex.getIndex(view) + ";");
			ps.println("			}");
			ps.println();
			ps.println("			@Override");
			ps.println("			public ViewLifecycle getLifecycle() {");
			ps.println("				return ViewLifecycle." + view.getLifecycle() + ";");
			ps.println("			}");
			ps.println();
			ps.println("			@Override");
			ps.println("			public Class<? extends View> getViewClass() {");
			ps.println("				return " + service.getFullName() + "." + view.getFullName() + ".class;");
			ps.println("			}");
			ps.println();
			ps.println("			@Override");
			ps.println("			public long getTick() {");
			ps.println("				return " + view.getTick() + ";");
			ps.println("			}");
			ps.println("		});");
			ps.println("	}");
			ps.println();
			r.add("		" + classname + ".add" + methodid + "(vss);");
			methodid++;
		}
		if (ps != null) {
			ps.println("}");
			ps.println();
			map.put(classname, baos.toString());
			ps.close();
		}
		return new Pair<List<String>, Map<String, String>>(r, map);
	}

	private void makeServerOnly() {
		String space = service.getFullName() + "." + namespace.getFullName();
		File base = new File(genDir, space.replace(".", File.separator));
		Pair<List<String>, Map<String, String>> stubs = printViewStubs(space);
		final VarDefines varDefines = new VarDefines(this);
		try (PrintStream ps = FileOperation.fopen(base, "ViewManager.java")) {
			ps.println("package " + space + ";");
			ps.println();
			ps.println("import java.util.ArrayList;");
			ps.println("import java.util.Collection;");
			ps.println();

			varDefines.printImport(ps);
			ps.println("import limax.provider.ViewContext;");
			ps.println("import limax.provider.ViewStub;");
			ps.println();
			ps.println("public final class ViewManager {");
			ps.println("	private static ViewContext viewcontext;");
			ps.println();
			ps.println("	@SuppressWarnings(\"unused\")");
			ps.println("	private static Collection<ViewStub> initialize(ViewContext viewcontext) {");
			ps.println("		ViewManager.viewcontext = viewcontext;");
			ps.println("		Collection<ViewStub> vss = new ArrayList<>();");
			ps.println();
			stubs.getKey().forEach(s -> ps.println(s));
			ps.println();
			ps.println("		return vss;");
			ps.println("	}");
			ps.println();

			ps.println("	public static ViewContext getViewContext() {");
			ps.println("		return viewcontext;");
			ps.println("	}");
			ps.println();

			if (Main.scriptSupport) {
				ps.println("	@SuppressWarnings(\"unused\")");
				ps.println("	private static String getDataDictionary() {");
				ps.println("		return \"" + String.join(",", dictionary.keySet()) + "\";");
				ps.println("	}");
				ps.println();
			}
			Map<String, String> varAdditional = varDefines.printMethod(ps, space);
			ps.println("}");
			if (!stubs.getValue().isEmpty())
				stubs.getValue().forEach((classname, content) -> {
					try (PrintStream psa = FileOperation.fopen(base, classname + ".java")) {
						psa.print(content);
					}
				});
			if (varAdditional == null)
				return;
			varAdditional.forEach((classname, content) -> {
				try (PrintStream psa = FileOperation.fopen(base, classname + ".java")) {
					psa.print(content);
				}
			});
		}
	}

	private void makeJavaClientOnly() {
		String space = service.getFullName() + "." + namespace.getFullName();
		File base = new File(genDir, space.replace(".", File.separator));
		try (PrintStream ps = FileOperation.fopen(base, "ViewManager.java")) {
			ps.println("package " + space + ";");
			ps.println();
			ps.println("import java.util.Map;");
			ps.println("import java.util.HashMap;");
			ps.println();
			ps.println("import limax.endpoint.View;");
			ps.println();
			ps.println("public final class ViewManager implements View.StaticManager {");
			ps.println("	private final int pvid;");
			ps.println("	private final Map<Short, Class<? extends View>> classes;");
			ps.println();
			ps.println("	private ViewManager(int pvid) {");
			ps.println("		this.pvid = pvid;");
			ps.println("		Map<Short, Class<? extends View>> map = new HashMap<Short, Class<? extends View>>();");
			for (final View view : views)
				ps.println("		map.put((short)" + viewindex.getIndex(view) + ", " + service.getFullName() + "."
						+ view.getFullName() + ".class);");
			ps.println("		this.classes = java.util.Collections.unmodifiableMap(map);");
			ps.println("	}");
			ps.println();
			ps.println("	public static ViewManager createInstance(int pvid) {");
			ps.println("		return new ViewManager(pvid);");
			ps.println("	}");
			ps.println();
			ps.println("	@Override");
			ps.println("	public int getProviderId() {");
			ps.println("		return pvid;");
			ps.println("	}");
			ps.println();
			ps.println("	@Override");
			ps.println("	public Map<Short, Class<? extends View>> getClasses() {");
			ps.println("		return classes;");
			ps.println("	}");
			ps.println("}");
		}
	}

	public static void makeJavaScriptTemplate(Collection<View> views) {
		List<String> pvids = views.stream().map(v -> v.getPvid()).distinct().map(i -> String.valueOf(i))
				.collect(Collectors.toList());
		try (PrintStream ps = FileOperation.fopen("template.js")) {
			ps.println("var providers = [" + String.join(",", pvids) + "];");
			ps.println("var cache = { } // if load from JavaScriptHandle remove it");
			ps.println("var ontunnel = function(pvid, label, data) // if load from JavaScriptHandle remove it");
			ps.println("var limax = Limax(function(ctx) {");
			ps.println("ctx.onerror = function(e) {");
			ps.println("	console.error('limax error', e);");
			ps.println("}");
			ps.println("ctx.onclose = function(e) {");
			ps.println("	console.log('limax close', e);");
			ps.println("}");
			ps.println("ctx.onopen = function() {");
			ps.println("	var type = ['NEW', 'REPLACE', 'TOUCH', 'DELETE' ];");
			pvids.forEach(pvid -> ps.println("	var v" + pvid + " = ctx[" + pvid + "];"));
			views.forEach(view -> {
				String name = view.getFullName();
				String ctx = "v" + view.getPvid() + ".";
				ps.println("	" + ctx + name + ".onchange = function(e) {");
				ps.println("		console.log(\"" + ctx + name
						+ ".onchange\", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);");
				ps.println("	}");
				if (view.getLifecycle() == ViewLifecycle.temporary) {
					ps.println("	" + ctx + name + ".onopen = function(instanceid, memberids) {");
					ps.println("		this[instanceid].onchange = this.onchange;");
					ps.println("		console.log(\"" + ctx + name
							+ ".onopen\", this[instanceid], instanceid, memberids);");
					ps.println("	}");
					ps.println("	" + ctx + name + ".onattach = function(instanceid, memberid) {");
					ps.println("		console.log(\"" + ctx + name + ".onattach\", this, instanceid, memberid);");
					ps.println("	}");
					ps.println("	" + ctx + name + ".ondetach = function(instanceid, memberid, reason) {");
					ps.println(
							"		console.log(\"" + ctx + name + ".ondetach\", this, instanceid, memberid, reason);");
					ps.println("	}");
					ps.println("	" + ctx + name + ".onclose = function(instanceid) {");
					ps.println("		console.log(\"" + ctx + name + ".onclose\", this, instanceid);");
					ps.println("	}");
				}
			});
			ps.println("}}, cache, ontunnel);");
			ps.println("var login = {");
			ps.println("	scheme : 'ws',");
			ps.println("	host : '127.0.0.1',");
			ps.println("	username : '',");
			ps.println("	token : '',");
			ps.println("	platflag : '',");
			ps.println("	pvids : [" + pvids.stream().map(String::valueOf).collect(Collectors.joining(",")) + "],");
			ps.println("}");
			ps.println("var connector = WebSocketConnector(limax, login);");

		}
	}

	public static void makeLuaTemplate(Collection<View> views) {
		List<String> pvids = views.stream().map(v -> v.getPvid()).distinct().map(i -> String.valueOf(i))
				.collect(Collectors.toList());
		try (PrintStream ps = FileOperation.fopen("template.lua")) {
			ps.println();
			ps.println("local providers = {" + String.join(", ", pvids) + "}");
			ps.println();
			ps.println("local function initialize(ctx)");
			ps.println("  local type = {[0] = 'NEW', [1] = 'REPLACE', [2] = 'TOUCH', [3] = 'DELETE'}");
			pvids.forEach(pvid -> ps.println("  local v" + pvid + " = ctx[" + pvid + "]"));
			views.forEach(view -> {
				String name = view.getFullName();
				String ctx = "v" + view.getPvid() + ".";
				ps.println("  " + ctx + name + ".onchange = function(e)");
				ps.println("    print(\"" + ctx + name
						+ ".onchange\", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);");
				ps.println("  end");
				if (view.getLifecycle() == ViewLifecycle.temporary) {
					ps.println("  " + ctx + name + ".onopen = function(this, instanceid, memberids)");
					ps.println("    this[instanceid].onchange = this.onchange");
					ps.println("    print('" + ctx + name + ".onopen', this[instanceid], instanceid, memberids)");
					ps.println("  end");
					ps.println("  " + ctx + name + ".onattach = function(this, instanceid, memberid)");
					ps.println("    print('" + ctx + name + ".onattach', this[instanceid], memberid);");
					ps.println("  end");
					ps.println("  " + ctx + name + ".ondetach = function(this, instanceid, memberid, reason)");
					ps.println("    print('" + ctx + name + ".ondetach', this[instanceid], memberid, reason)");
					ps.println("  end");
					ps.println("  " + ctx + name + ".onclose = function(this, instanceid)");
					ps.println("    print('" + ctx + name + ".onclose', this[instanceid])");
					ps.println("  end");
				}
			});
			ps.println("end");
			ps.println();
			ps.println("return { callback = function(ctx)");
			ps.println("  ctx.onerror = function(e)");
			ps.println("    print('limax error', tostring(e))");
			ps.println("  end");
			ps.println("  ctx.onclose = function(e)");
			ps.println("    print('limax close', tostring(e));");
			ps.println("  end");
			ps.println("  ctx.onopen = function()");
			ps.println("    xpcall(");
			ps.println("      function ()");
			ps.println("        initialize(ctx)");
			ps.println("      end,");
			ps.println("      function (msg)");
			ps.println("        print('LUA ERROR: ', tostring(msg), '\\n')");
			ps.println("        print(debug.traceback())");
			ps.println("      end)");
			ps.println("  end");
			ps.println("end, pvids = providers }");
			ps.println();

		}
	}
}
