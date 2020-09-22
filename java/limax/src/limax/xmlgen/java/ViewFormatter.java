package limax.xmlgen.java;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import limax.codec.StringStream;
import limax.provider.ViewLifecycle;
import limax.util.StringUtils;
import limax.xmlgen.Bind;
import limax.xmlgen.Cbean;
import limax.xmlgen.Control;
import limax.xmlgen.FileOperation;
import limax.xmlgen.Main;
import limax.xmlgen.NameStringToIndex;
import limax.xmlgen.Naming;
import limax.xmlgen.Subscribe;
import limax.xmlgen.Table;
import limax.xmlgen.Type;
import limax.xmlgen.TypeBinary;
import limax.xmlgen.TypeList;
import limax.xmlgen.TypeMap;
import limax.xmlgen.TypeSet;
import limax.xmlgen.TypeVector;
import limax.xmlgen.Variable;
import limax.xmlgen.View;
import limax.xmlgen.Xbean;
import limax.xmlgen.java.Viewgen.Marshals;

class ViewFormatter {
	private final Viewgen viewgen;
	private final View view;
	private final String namespace;
	private final String nsprovider;
	private final String name;
	private final int index;
	private final Set<BindWrapper> touch = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<BindWrapper> fieldcollector = Collections.newSetFromMap(new IdentityHashMap<>());
	private final TableMap tablemap;

	class BindWrapper {
		private final Bind bind;
		private final Map<String, Integer> map = new HashMap<>();

		BindWrapper(Bind bind) {
			this.bind = bind;
		}

		boolean update(Variable var, int index) {
			if (bind.isFullBind()) {
				map.put(var.getName(), index);
				return true;
			} else {
				int i = 0;
				for (Variable v : bind.getVariables()) {
					if (v.getName().equals(var.getName())) {
						map.put(v.getName(), i);
						return true;
					}
					i++;
				}
				return false;
			}
		}

		String getName() {
			return bind.getName();
		}

		int getIndex(Variable var) {
			return map.get(var.getName());
		}

		boolean contains(Variable var) {
			return map.containsKey(var.getName());
		}

		Bind getInner() {
			return bind;
		}
	}

	class TableBind {
		private final Table table;
		private final List<BindWrapper> binds;
		private final List<Variable> vars = new ArrayList<>();
		private final boolean fullbind;

		TableBind(Table table, List<Bind> binds) {
			this.table = table;
			this.binds = binds.stream().map(bind -> new BindWrapper(bind)).collect(Collectors.toList());
			this.fullbind = binds.stream().anyMatch(bind -> bind.isFullBind());
			if (table.getValueType() instanceof Xbean) {
				AtomicInteger index = new AtomicInteger();
				Xbean xbean = (Xbean) table.getValueType();
				xbean.getVariables().forEach(var -> {
					if (this.binds.stream().map(bind -> bind.update(var, index.intValue())).reduce(false,
							(a, b) -> a || b)) {
						vars.add(var);
						index.incrementAndGet();
					}
				});
			}
		}

		boolean isFullBind() {
			return fullbind;
		}

		List<Variable> getVariables() {
			return vars;
		}

		Table getTable() {
			return table;
		}

		Type getValueType() {
			return table.getValueType();
		}

		List<BindWrapper> getBinds() {
			return binds;
		}

		void printFieldListener(PrintStream ps, Variable var, int i, Marshals marshals) {
			ps.println("		@Override");
			ps.println("		public void onChanged(Object key, Object value, String name, limax.zdb.Note note) {");
			if (view.getLifecycle() != ViewLifecycle.global) {
				ps.println("			Set<_" + name + "> views = _map_" + table.getName() + "_.get(key);");
				ps.println("			if (views == null)");
				ps.println("				return;");
				if (Main.scriptSupport)
					ps.println("			boolean script = views.stream().anyMatch(view -> view.isScriptEnabled());");
				String m = marshals.get(var);
				if (var.getType() instanceof TypeMap) {
					Type kt = ((TypeMap) var.getType()).getKeyType();
					Type vt = ((TypeMap) var.getType()).getValueType();
					String ts = "limax.zdb.NoteMap<" + TypeName.getBoxingName(kt) + ", " + TypeName.getBoxingName(vt)
							+ ">";
					String mk = marshals.get(((TypeMap) var.getType()).getKeySetType());
					ps.println("			@SuppressWarnings(\"unchecked\")");
					ps.println("			" + ts + " _n_ = (" + ts + ")note;");
					if (Main.scriptSupport) {
						ps.println("			String _t_;");
						ps.println("			if (script)");
						ps.println("			{");
						ps.println("				limax.codec.StringStream ss = limax.codec.StringStream.create(\"Z"
								+ StringStream.pack((Integer) var.attachment()) + ":M\");");
						String mv = marshals.get(((TypeMap) var.getType()).getKeyType());
						ps.println("				_n_.getChanged().forEach((_k_, _v_) -> ss.append(" + mv
								+ "(\"\", _k_)).append(" + marshals.get(((TypeMap) var.getType()).getValueType())
								+ "(\"\", _v_)));");
						ps.println("				ss.append(\":P\");");
						ps.println("				_n_.getRemoved().keySet().forEach(_v_ -> ss.append(" + mv
								+ "(\"\", _v_)));");
						ps.println("				_t_ = ss.toString(\":\");");
						ps.println("			} else ");
						ps.println("				_t_ = \"\";");

					}
					ps.println("			limax.codec.Octets _a_ = " + m + "(_n_.getChanged());");
					ps.println("			limax.codec.Octets _r_ = " + mk + "(_n_.getRemoved().keySet());");
					binds.stream().filter(bind -> bind.contains(var)).forEach(bind -> {
						ps.println("			_field_collector_" + bind.getName()
								+ "_.computeIfAbsent(key, _k_ -> new ArrayList<>()).add(new ViewDataCollector.MutableField((byte)"
								+ bind.getIndex(var) + ", _a_, _r_, " + (Main.scriptSupport ? "_t_" : "\"\"") + "));");
					});
				} else if (var.getType() instanceof TypeSet) {
					String ts = "limax.zdb.NoteSet<" + TypeName.getBoxingName(((TypeSet) var.getType()).getValueType())
							+ ">";
					ps.println("			@SuppressWarnings(\"unchecked\")");
					ps.println("			" + ts + " _n_ = (" + ts + ")note;");
					if (Main.scriptSupport) {
						ps.println("			String _t_;");
						ps.println("			if (script)");
						ps.println("			{");
						ps.println("				limax.codec.StringStream ss = limax.codec.StringStream.create(\"Y"
								+ StringStream.pack((Integer) var.attachment()) + ":P\");");
						String mk = marshals.get(((TypeSet) var.getType()).getValueType());
						ps.println("				_n_.getAdded().forEach(var -> ss.append(" + mk + "(\"\", var)));");
						ps.println("				ss.append(\":P\");");
						ps.println(
								"				_n_.getRemoved().forEach(var -> ss.append(" + mk + "(\"\", var)));");
						ps.println("				_t_ = ss.toString(\":\");");
						ps.println("			} else ");
						ps.println("				_t_ = \"\";");
					}
					ps.println("			limax.codec.Octets _a_ = " + m + "(_n_.getAdded());");
					ps.println("			limax.codec.Octets _r_ = " + m + "(_n_.getRemoved());");
					binds.stream().filter(bind -> bind.contains(var)).forEach(bind -> {
						ps.println("			_field_collector_" + bind.getName()
								+ "_.computeIfAbsent(key, _k_ -> new ArrayList<>()).add(new ViewDataCollector.MutableField((byte)"
								+ bind.getIndex(var) + ", _a_, _r_, " + (Main.scriptSupport ? "_t_" : "\"\"") + "));");
					});
				} else {
					ps.println("			" + TypeName.getName(table.getValueType()) + " _p_ = ("
							+ TypeName.getName(table.getValueType()) + ")value;");
					String f = StringUtils.upper1(var.getName());
					if (var.getType() instanceof TypeBinary)
						f += "OctetsCopy";
					ps.println("			limax.codec.Octets _d_ = " + m + "(_p_.get" + f + "());");
					ps.println("			String _t_ = " + (Main.scriptSupport
							? "script ? " + m + "(\"X" + StringStream.pack((Integer) var.attachment()) + ":\", _p_.get"
									+ f + "()) : \"\""
							: "\"\"") + ";");
					binds.stream().filter(bind -> bind.contains(var)).forEach(bind -> {
						ps.println("			_field_collector_" + bind.getName()
								+ "_.computeIfAbsent(key, _k_ -> new ArrayList<>()).add(new ViewDataCollector.ImmutableField((byte)"
								+ bind.getIndex(var) + ", _d_, _t_));");
					});
				}
			} else {
				binds.stream().filter(bind -> bind.contains(var)).forEach(bind -> {
					if (touch.contains(bind))
						ps.println("			_touch_" + bind.getName() + "_.put(key, true);");
				});
			}
			ps.println("		}");
		}

		void printListener(PrintStream ps, TableBind tb) {
			ps.println("		@Override");
			ps.println("		public void onChanged(Object key, Object value) {");
			ps.println("			Set<_" + name + "> views = _map_" + table.getName() + "_.get(key);");
			ps.println("			if (views == null)");
			ps.println("				return;");
			ps.println("			views.forEach(view -> {");
			binds.forEach(bind -> ps.println("				view.set" + StringUtils.upper1(bind.getName()) + "(("
					+ TypeName.getName(table.getValueType()) + ")value);"));
			ps.println("			});");
			ps.println("		}");
			ps.println();
			ps.println("		@Override");
			ps.println("		public void onChanged(Object key, Object value, String name, limax.zdb.Note note) {");
			ps.println("			Set<_" + name + "> views = _map_" + table.getName() + "_.get(key);");
			ps.println("			if (views == null)");
			ps.println("				return;");
			AtomicInteger i = new AtomicInteger();
			binds.forEach(bind -> {
				if (touch.contains(bind)) {
					ps.println("			if (_touch_" + bind.getName() + "_.put(key, false))");
					ps.println("				views.forEach(view -> view.set" + StringUtils.upper1(bind.getName())
							+ "((" + TypeName.getName(table.getValueType()) + ")value));");
				} else if (fieldcollector.contains(bind)) {
					String collectorName = "_field_collector_" + bind.getName() + "_";
					ps.println("			List<ViewDataCollector.Field> _f" + i.intValue() + "_ = " + collectorName
							+ ".remove(key);");
					ps.println("			if (_f" + i.intValue() + "_ != null)");
					int index = i.getAndIncrement();
					ps.println("				views.forEach(view -> view.set" + StringUtils.upper1(bind.getName())
							+ "((" + TypeName.getName(table.getValueType()) + ")value"
							+ (bind.getInner().isImmutable() ? "" : (", _f" + index + "_")) + "));");
				} else {
					ps.println("			views.forEach(view -> view.set" + StringUtils.upper1(bind.getName()) + "(("
							+ TypeName.getName(table.getValueType()) + ")value));");
				}
			});
			ps.println("		}");
			ps.println();
			ps.println("		@Override");
			ps.println("		public void onRemoved(Object key, Object value) {");
			ps.println("			Set<_" + name + "> views = _map_" + table.getName() + "_.get(key);");
			ps.println("			if (views == null)");
			ps.println("				return;");
			ps.println("			views.forEach(view -> {");
			binds.forEach(
					bind -> ps.println("				view.set" + StringUtils.upper1(bind.getName()) + "(null);"));
			ps.println("			});");
			ps.println("		}");
		}
	}

	class TableMap {
		private final Map<Table, TableBind> map;

		TableMap() {
			this.map = view.getBinds().stream().collect(Collectors.groupingBy(Bind::getTable)).entrySet().stream()
					.collect(Collectors.toMap(e -> e.getKey(), e -> new TableBind(e.getKey(), e.getValue())));
		}

		void printMap(PrintStream ps) {
			map.keySet().forEach(t -> ps.println("	private final static Map<Object, Set<_" + name + ">> _map_"
					+ t.getName() + "_ = new ConcurrentHashMap<>();"));
		}

		void printBinds(PrintStream ps) {
			map.values().forEach(tb -> {
				String tname = tb.getTable().getName();
				Type type = tb.getValueType();
				String objtype;
				if (type instanceof Xbean)
					objtype = ((Xbean) type).getFullName();
				else if (type instanceof Cbean)
					objtype = ((Cbean) type).getFullName();
				else
					objtype = TypeName.getBoxingName(type);
				ps.println("	public final void bind" + StringUtils.upper1(tname) + "("
						+ TypeName.getName(tb.getTable().getKeyType()) + " key, Runnable done) {");
				ps.println("		limax.zdb.Procedure.execute(() -> {");
				ps.println("			" + objtype + " obj = table." + StringUtils.upper1(tname) + ".select(key);");
				ps.println("			if (obj != null) {");
				tb.getBinds().forEach(
						bind -> ps.println("				set" + StringUtils.upper1(bind.getName()) + "(obj);"));
				ps.println("			}");
				tb.getBinds().stream().filter(bind -> touch.contains(bind)).forEach(bind -> {
					String bindname = bind.getName();
					ps.println("			_touch_" + bindname + "_.put(key, false);");
					ps.println("			try {");
					ps.println(
							"				Resource.create(resource, () -> _touch_" + bindname + "_.remove(key));");
					ps.println("			} catch (IllegalStateException e) {");
					ps.println("				return false;");
					ps.println("			}");
				});
				ps.println(
						"			_map_" + tname + "_" + ".computeIfAbsent(key, k -> new HashSet<>()).add(this);");
				ps.println("			try {");
				ps.println("				Resource.create(resource, () -> limax.zdb.Procedure.execute(() -> {");
				ps.println("					table." + StringUtils.upper1(tname) + ".select(key);");
				ps.println("					_map_" + tname + "_.computeIfPresent(key, (k, v) -> {");
				ps.println("						v.remove(this);");
				ps.println("						return v.isEmpty() ? null : v;");
				ps.println("					});");
				ps.println("					return true;");
				ps.println("				}));");
				ps.println("			} catch (IllegalStateException e) {");
				ps.println("				return false;");
				ps.println("			}");
				ps.println("			return true;");
				ps.println("		}, (p, r) -> { if (done != null) done.run(); } );");
				ps.println("	}");
				ps.println();
				ps.println("	public final void bind" + StringUtils.upper1(tname) + "("
						+ TypeName.getName(tb.getTable().getKeyType()) + " key) {");
				ps.println("		bind" + StringUtils.upper1(tname) + "(key, null);");
				ps.println("	}");
				ps.println();
			});
		}

		void printListeners(PrintStream ps, Marshals marshals) {
			List<String> registerlines = new ArrayList<>();
			AtomicInteger lindex = new AtomicInteger();
			map.values().forEach(tb -> {
				Type type = tb.getValueType();
				AtomicInteger field = new AtomicInteger();
				List<Variable> variables = null;
				if (view.getLifecycle() == ViewLifecycle.global) {
					if (type instanceof Xbean && !tb.isFullBind())
						variables = tb.getVariables();
				} else {
					if (type instanceof Xbean)
						variables = (tb.isFullBind() ? ((Xbean) type).getVariables() : tb.getVariables());
				}
				if (variables != null)
					variables.forEach(var -> {
						ps.println("	private final static limax.zdb.Listener l" + lindex.intValue()
								+ " = new limax.zdb.Listener() {");
						tb.printFieldListener(ps, var, field.getAndIncrement(), marshals);
						registerlines.add("		Resource.create(__parent__, table."
								+ StringUtils.upper1(tb.getTable().getName()) + ".get().addListener(l"
								+ lindex.getAndIncrement() + ", \"" + var.getName() + "\"));");
						ps.println("	};");
						ps.println();
					});
				ps.println("	private final static limax.zdb.Listener l" + lindex.intValue()
						+ " = new limax.zdb.Listener() {");
				registerlines
						.add("		Resource.create(__parent__, table." + StringUtils.upper1(tb.getTable().getName())
								+ ".get().addListener(l" + lindex.getAndIncrement() + "));");
				tb.printListener(ps, tb);
				ps.println("	};");
				ps.println();
			});
			ps.println("	static void registerTableListener(Resource __parent__) {");
			registerlines.forEach(line -> ps.println(line));
			ps.println("	}");
			ps.println();
		}

		Collection<TableBind> getTableBinds() {
			return map.values();
		}
	}

	private final NameStringToIndex varnameindex;

	public ViewFormatter(Viewgen viewgen, View view, int index) {
		this.viewgen = viewgen;
		this.view = view;
		this.namespace = viewgen.makeViewNameSpace(view);
		this.nsprovider = viewgen.getProviderNamespace();
		this.name = view.getLastName();
		this.index = index;
		this.varnameindex = view.getMemberNameStringToIndex();
		this.tablemap = new TableMap();
	}

	View getView() {
		return view;
	}

	String getProviderNamespace() {
		return nsprovider;
	}

	int getMemberIndex(Naming var) {
		return varnameindex.getIndex(var);
	}

	private boolean hasVariables() {
		return view.getVariables().size() > 0 || view.getBinds().size() > 0 || view.getSubscribes().size() > 0;
	}

	public void makeClient(File genDir, File srcDir) {
		File output = new File(genDir, namespace.replace(".", File.separator));
		if (ViewLifecycle.temporary == view.getLifecycle()) {
			try (final PrintStream ps = FileOperation.fopen(output, "_" + name + ".java")) {
				ps.println();
				ps.println("package " + namespace + ";");
				ps.println();
				if (hasVariables())
					ps.println("import limax.codec.OctetsStream;");
				ps.println("import limax.codec.MarshalException;");
				ps.println("import limax.endpoint.Endpoint;");
				ps.println("import limax.endpoint.EndpointManager;");
				ps.println("import limax.endpoint.ViewContext;");
				if (hasVariables()) {
					ps.println("import limax.endpoint.ViewChangedType;");
					ps.println("import limax.endpoint.ViewVisitor;");
				}

				ps.println(view.getComment());
				ps.println("public abstract class _" + name + " extends limax.endpoint.TemporaryView {");
				printClientDefine(ps, true);
				ps.println("}");
				ps.println();
			}
			output = new File(srcDir, namespace.replace(".", File.separator));
			if (new File(output, name + ".java").isFile())
				return;
			try (final PrintStream ps = FileOperation.fopen(output, name + ".java")) {
				ps.println();
				ps.println("package " + namespace + ";");
				ps.println();
				ps.println("import limax.endpoint.ViewContext;");
				ps.println();
				ps.println("public final class " + name + " extends _" + name + " {");
				ps.println("	private " + name + "(ViewContext vc) {");
				ps.println("		super(vc);");
				ps.println("	}");
				ps.println();
				ps.println("	protected void onOpen(java.util.Collection<Long> sessionids) {");
				ps.println("		// register listener here");
				ps.println("	}");
				ps.println();
				ps.println("	protected void onClose() {");
				ps.println("	}");
				ps.println();
				ps.println("	protected void onAttach(long sessionid) {");
				ps.println("	}");
				ps.println();
				ps.println("	protected void onDetach(long sessionid, byte reason) {");
				ps.println("		if (reason >= 0) {");
				ps.println("			//Application reason");
				ps.println("		} else {");
				ps.println("			//Connection abort reason");
				ps.println("		}");
				ps.println("	}");
				ps.println();
				ps.println("}");
				ps.println();
			}
		} else {
			try (final PrintStream ps = FileOperation.fopen(output, name + ".java")) {
				ps.println();
				ps.println("package " + namespace + ";");
				ps.println();
				if (hasVariables())
					ps.println("import limax.codec.OctetsStream;");
				ps.println("import limax.codec.MarshalException;");
				ps.println("import limax.endpoint.Endpoint;");
				ps.println("import limax.endpoint.EndpointManager;");
				ps.println("import limax.endpoint.ViewContext;");
				if (hasVariables()) {
					ps.println("import limax.endpoint.ViewChangedType;");
					ps.println("import limax.endpoint.ViewVisitor;");
				}
				ps.println(view.getComment());
				ps.println("public final class " + name + " extends limax.endpoint.View {");
				printClientDefine(ps, false);
				ps.println("}");
				ps.println();
			}
		}
	}

	private String getClientBindType(Bind bind) {
		return bind.isFullBind() ? TypeName.getBoxingName(bind.getValueType()) : bind.getName();
	}

	String getServerBindType(View view, Bind bind) {
		return bind.isFullBind() ? TypeName.getName(bind.getValueType()) : "_" + view.getName() + "." + bind.getName();
	}

	private String getType(Subscribe ref, TypeName.Purpose purpose) {
		if (ref.getVariable() != null)
			return TypeName.getName(ref.getVariable().getType(), purpose);
		Bind bind = ref.getBind();
		return bind.isFullBind() ? TypeName.getName(bind.getValueType())
				: viewgen.getViewFormatter(ref.getView()).namespace + "." + ref.getView().getName() + "."
						+ bind.getName();
	}

	private void printViewChangeType(PrintStream ps, View view, int start) {
		ps.println("		boolean __o_flags__;");
		if (view.getSubscribes().isEmpty()) {
			ps.println("		__o_flags__ = __flags__.get(index);");
			ps.println("		__flags__.set(index);");
		} else {
			ps.println("		if (index < " + start + ") {");
			ps.println("			__o_flags__ = __flags__.get(index);");
			ps.println("			__flags__.set(index);");
			ps.println("		} else {");
			ps.println("			__o_flags__ = __subsFlags__.get(sessionid, index - " + start + ");");
			ps.println("			__subsFlags__.set(sessionid, index - " + start + ");");
			ps.println("		}");
		}
		ps.println("		ViewChangedType _t_ = __o_flags__ ? ViewChangedType.REPLACE : ViewChangedType.NEW;");

	}

	private void printXbeanBindUpdate(PrintStream ps, Bind bind) {
		AtomicInteger i = new AtomicInteger();
		ps.println("			switch(field) {");
		(bind.isFullBind() ? ((Xbean) bind.getValueType()).getVariables() : bind.getVariables()).forEach(var -> {
			ps.println("			case " + i.getAndIncrement() + ": {");
			Type type = var.getType();
			String field = "_s_." + var.getName();
			if (type instanceof TypeMap) {
				Unmarshal.make(type, field, ps, "				");
				ps.println("				_os_ = OctetsStream.wrap(dataremoved);");
				ConstructWithUnmarshal.make(((TypeMap) type).getKeySetType().asTypeVector(), "_r_", ps,
						"				");
				ps.println("				" + field + ".keySet().removeAll(_r_);");
			} else if (type instanceof TypeSet) {
				Unmarshal.make(type, field, ps, "				");
				ps.println("				_os_ = OctetsStream.wrap(dataremoved);");
				ConstructWithUnmarshal.make(((TypeSet) type).asTypeVector(), "_r_", ps, "				");
				ps.println("				" + field + ".removeAll(_r_);");
			} else {
				if (type instanceof TypeVector || type instanceof TypeList)
					ps.println("				" + field + ".clear();");
				Unmarshal.make(type, field, ps, "				");
			}
			ps.println("				break;");
			ps.println("			}");
		});
		ps.println("			}");
	}

	private void printClientDefine(PrintStream ps, boolean temporary) {
		int start = view.getVariables().size() + view.getBinds().size();
		ps.println("	private static int __pvid__;");
		if (hasVariables()) {
			ps.println("	private final java.util.BitSet __flags__ = new java.util.BitSet();");
			if (!view.getSubscribes().isEmpty())
				ps.println(
						"	private final limax.util.MapBitSet<Long> __subsFlags__ = new limax.util.MapBitSet<Long>();");
			ps.println("	private ViewChangedType __type__ = ViewChangedType.TOUCH;");
			ps.println();
		}
		Declare.make(view.getEnums(), view.getVariables(), Declare.Type.PRIVATE, ps, "	");
		view.getBinds()
				.forEach(bind -> ps.println("	private " + getClientBindType(bind) + " " + bind.getName() + ";"));
		if (!view.getBinds().isEmpty())
			ps.println();
		view.getSubscribes().forEach(subs -> {
			String type = getType(subs, TypeName.Purpose.CONTAINER_ITEM);
			ps.println("	private final java.util.Map<Long, " + type + "> " + subs.getName()
					+ " = new java.util.HashMap<Long, " + type + ">();");
		});
		if (!view.getSubscribes().isEmpty())
			ps.println();
		view.getVariables().forEach(var -> {
			ps.println("	public synchronized void visit" + StringUtils.upper1(var.getName()) + "(ViewVisitor<"
					+ TypeName.getBoxingName(var.getType()) + "> v) {");
			ps.println("		if(__flags__.get(" + varnameindex.getIndex(var) + "))");
			ps.println("			v.accept(" + var.getName() + ");");
			ps.println("	}");
			ps.println();
		});
		view.getBinds().forEach(bind -> {
			ps.println("	public synchronized void visit" + StringUtils.upper1(bind.getName()) + "(ViewVisitor<"
					+ getClientBindType(bind) + "> v) {");
			ps.println("		if(__flags__.get(" + varnameindex.getIndex(bind) + "))");
			ps.println("			v.accept(" + bind.getName() + ");");
			ps.println("	}");
			ps.println();
		});
		view.getSubscribes().forEach(subs -> {
			ps.println("	public synchronized void visit" + StringUtils.upper1(subs.getName())
					+ "(ViewVisitor<java.util.Map<Long, " + getType(subs, TypeName.Purpose.CONTAINER_ITEM) + ">> v) {");
			ps.println("		v.accept(" + subs.getName() + ");");
			ps.println("	}");
			ps.println();
		});
		Construct.make(view, view.getName(), ps, "	", false, temporary);
		// override getClassIndex
		ps.println("	@Override");
		ps.println("	protected final short getClassIndex() {");
		ps.println("		return " + index + ";");
		ps.println("	}");
		ps.println();

		if (view.getLifecycle() == ViewLifecycle.temporary) {
			ps.println("	protected final void detach(long sessionid, byte reason) {");
			ps.println("		onDetach(sessionid, reason);");
			boolean f = false;
			for (final Subscribe subs : view.getSubscribes()) {
				ps.println("		this." + subs.getName() + ".remove(sessionid);");
				f = true;
			}
			if (f)
				ps.println("		this.__subsFlags__.remove(sessionid);");
			ps.println("	}");
			ps.println();
		}

		ps.println("	@Override");
		ps.println(
				"	protected final void onData(long sessionid, byte index, byte field, limax.codec.Octets data, limax.codec.Octets dataremoved) throws MarshalException {");
		if (hasVariables()) {
			ps.println("		if (index < 0)");
			ps.println("			onRemoved(sessionid, (byte) (index & 0x7f));");
			ps.println("		else if (data.size() == 0)");
			ps.println("			onChanged(sessionid, index);");
			ps.println("		else if (field < 0)");
			ps.println("			onChanged(sessionid, index, data);");
			ps.println("		else");
			ps.println("			onChanged(sessionid, index, field, data, dataremoved);");
		}
		ps.println("	}");
		ps.println();
		if (hasVariables()) {
			ps.println(
					"	private void onChanged(long sessionid, byte index, byte field, limax.codec.Octets data, limax.codec.Octets dataremoved) throws MarshalException {");
			if (Stream.concat(
					view.getBinds().stream().filter(bind -> bind.getValueType() instanceof Xbean)
							.map(bind -> (Xbean) bind.getValueType()),
					view.getSubscribes().stream().filter(subs -> subs.getBind() != null)
							.filter(subs -> subs.getBind().getValueType() instanceof Xbean)
							.map(subs -> (Xbean) subs.getBind().getValueType()))
					.findAny().isPresent()) {
				printViewChangeType(ps, view, start);
				ps.println("		OctetsStream _os_ = OctetsStream.wrap(data);");
				ps.println("		switch(index) {");
				view.getBinds().stream().filter(bind -> bind.getValueType() instanceof Xbean).forEach(bind -> {
					ps.println("		case " + varnameindex.getIndex(bind) + ": {");
					Xbean xbean = (Xbean) bind.getValueType();
					String type = (bind.isFullBind() ? TypeName.getName(xbean) : bind.getName());
					String self = "this." + bind.getName();
					ps.println("			" + type + " _s_ = _t_ == ViewChangedType.REPLACE ? " + self + " : (" + self
							+ " = new " + type + "());");
					printXbeanBindUpdate(ps, bind);
					ps.println("			__type__ = _t_;");
					ps.println("			break;");
					ps.println("		}");
				});
				view.getSubscribes().stream().filter(subs -> subs.getBind() != null)
						.filter(subs -> subs.getBind().getValueType() instanceof Xbean).forEach(subs -> {
							ps.println("		case " + varnameindex.getIndex(subs) + ": {");
							ps.println("			" + getType(subs, TypeName.Purpose.TYPE) + " _s_;");
							ps.println("			if (_t_ == ViewChangedType.REPLACE)");
							ps.println("				_s_ = this." + subs.getName() + ".get(sessionid);");
							ps.println("			else");
							ps.println("				this." + subs.getName() + ".put(sessionid, _s_ = new "
									+ getType(subs, TypeName.Purpose.TYPE) + "());");
							printXbeanBindUpdate(ps, subs.getBind());
							ps.println("			__type__ = _t_;");
							ps.println("			break;");
							ps.println("		}");
						});
				ps.println("		default:");
				ps.println(
						"			throw new RuntimeException( \"view \\\"\" + this + \"\\\" mismatch bind index = \\\"\" + index + \"\\\"\");");
				ps.println("		}");
			}
			ps.println("	}");
			ps.println();
			ps.println(
					"	private void onChanged(long sessionid, byte index, limax.codec.Octets data) throws MarshalException {");
			printViewChangeType(ps, view, start);
			ps.println("		OctetsStream _os_ = OctetsStream.wrap(data);");
			ps.println("		switch(index) {");
			view.getVariables().forEach(var -> {
				ps.println("		case " + varnameindex.getIndex(var) + ": {");
				ConstructWithUnmarshal.make(var.getType(), "_n_", ps, "			");
				ps.println("			this." + var.getName() + " = _n_;");
				ps.println("			onViewChanged(sessionid, \"" + var.getName() + "\", _n_, _t_);");
				ps.println("			break;");
				ps.println("		}");
			});
			view.getBinds().forEach(bind -> {
				ps.println("		case " + varnameindex.getIndex(bind) + ": {");
				if (bind.isFullBind()) {
					ConstructWithUnmarshal.make(bind.getValueType(), "_n_", ps, "			");
				} else {
					ConstructWithUnmarshal.make(bind.getName(), "_n_", ps, "			");
				}
				ps.println("			this." + bind.getName() + " = _n_;");
				ps.println("			onViewChanged(sessionid, \"" + bind.getName() + "\", _n_, _t_);");
				ps.println("			break;");
				ps.println("		}");
			});
			view.getSubscribes().forEach(subs -> {
				ps.println("		case " + varnameindex.getIndex(subs) + ": {");
				Variable var = subs.getVariable();
				if (var != null) {
					ConstructWithUnmarshal.make(var.getType(), "_n_", ps, "			");
				} else {
					Bind bind = subs.getBind();
					if (bind.isFullBind()) {
						ConstructWithUnmarshal.make(bind.getValueType(), "_n_", ps, "			");
					} else {
						ConstructWithUnmarshal.make(getType(subs, TypeName.Purpose.TYPE), "_n_", ps, "			");
					}
				}
				ps.println("			" + subs.getName() + ".put(sessionid, _n_);");
				ps.println("			onViewChanged(sessionid, \"" + subs.getName() + "\", _n_, _t_);");
				ps.println("			break;");
				ps.println("		}");
			});
			ps.println("		default:");
			ps.println(
					"			throw new RuntimeException( \"view \\\"\" + this + \"\\\" lost var index = \\\"\" + index + \"\\\"\");");
			ps.println("		}");
			ps.println("	}");
			ps.println();

			ps.println("	private void onChanged(long sessionid, byte index, ViewChangedType type) {");
			ps.println("		switch(index) {");
			Stream.concat(view.getVariables().stream(), view.getBinds().stream()).forEach(var -> {
				ps.println("		case " + varnameindex.getIndex(var) + ":");
				ps.println("			onViewChanged(sessionid, \"" + var.getName() + "\", this." + var.getName()
						+ ", type);");
				ps.println("			break;");
			});
			view.getSubscribes().forEach(subs -> {
				ps.println("		case " + varnameindex.getIndex(subs) + ": {");
				ps.println("			onViewChanged(sessionid, \"" + subs.getName() + "\", " + subs.getName()
						+ ".get(sessionid), type);");
				ps.println("			break;");
				ps.println("		}");
			});
			ps.println("		default:");
			ps.println(
					"			throw new RuntimeException( \"view \\\"\" + this + \"\\\" lost var index = \\\"\" + index + \"\\\"\");");
			ps.println("		}");
			ps.println("	}");
			ps.println();

			ps.println("	private void onChanged(long sessionid, byte index) {");
			ps.println("		onChanged(sessionid, index, __type__);");
			ps.println("		__type__ = ViewChangedType.TOUCH;");
			ps.println("	}");
			ps.println();

			ps.println("	private void onRemoved(long sessionid, byte index) {");
			if (view.getSubscribes().isEmpty()) {
				ps.println("		__flags__.clear(index);");
			} else {
				ps.println("		if (index < " + start + ")");
				ps.println("			__flags__.clear(index);");
				ps.println("		else");
				ps.println("			__subsFlags__.clear(sessionid, index - " + start + ");");
			}
			ps.println("		onChanged(sessionid, index, ViewChangedType.DELETE);");
			ps.println("	}");
			ps.println();
		}

		final String fieldnamesset = "_fieldnames_" + view.getName();
		ps.println("	private final static java.util.Set<String> " + fieldnamesset + ";");
		ps.println("	static {");
		ps.println("		java.util.Set<String> set = new java.util.LinkedHashSet<String>();");
		Stream.concat(Stream.concat(view.getVariables().stream(), view.getBinds().stream()),
				view.getSubscribes().stream()).forEach(var -> ps.println("		set.add(\"" + var.getName() + "\");"));
		ps.println("		" + fieldnamesset + " = java.util.Collections.unmodifiableSet(set);");
		ps.println("	}");
		ps.println();
		ps.println("	@Override");
		ps.println("	public java.util.Set<String> getFieldNames() {");
		ps.println("		return " + fieldnamesset + ";");
		ps.println("	}");
		ps.println();
		if (ViewLifecycle.temporary == view.getLifecycle()) {
			ps.println("	public static " + view.getName() + " getInstance(int instanceindex) {");
			ps.println("		return getInstance(Endpoint.getDefaultEndpointManager(), instanceindex);");
			ps.println("	}");
			ps.println();
			ps.println("	public static " + view.getName()
					+ " getInstance(EndpointManager manager, int instanceindex) {");
			ps.println("		return getInstance(manager, __pvid__, instanceindex);");
			ps.println("	}");
			ps.println();
			ps.println("	public static " + view.getName()
					+ " getInstance(EndpointManager manager, int pvid, int instanceindex) {");
			ps.println("		ViewContext vc = " + "manager.getViewContext(pvid, ViewContext.Type.Static);");
			ps.println("		return vc == null ? null : (" + view.getName() + ")vc.findTemporaryView((short)" + index
					+ ", instanceindex);");
			ps.println("	}");
		} else {
			ps.println("	public static " + view.getName() + " getInstance() {");
			ps.println("		return getInstance(Endpoint.getDefaultEndpointManager());");
			ps.println("	}");
			ps.println();
			ps.println("	public static " + view.getName() + " getInstance(EndpointManager manager) {");
			ps.println("		return getInstance(manager, __pvid__);");
			ps.println("	}");
			ps.println();
			ps.println("	public static " + view.getName() + " getInstance(EndpointManager manager, int pvid) {");
			ps.println("		ViewContext vc = " + "manager.getViewContext(pvid, ViewContext.Type.Static);");
			ps.println("		return vc == null ? null : (" + view.getName() + ")vc.getSessionOrGlobalView((short)"
					+ index + ");");
			ps.println("	}");
		}
		ps.println();
		view.getBinds().forEach(bind -> new BindFormatter(bind, false).make(ps));
		view.getControls()
				.forEach(control -> new ControlFormatter(control, false, varnameindex.getIndex(control)).make(ps, "	"));
	}

	private String getViewParentClass() {
		switch (view.getLifecycle()) {
		case global:
			return "GlobalView";
		case session:
			return "SessionView";
		case temporary:
			return "TemporaryView";
		default:
			throw new RuntimeException("unknow know ViewLifecycle value " + view.getLifecycle());
		}
	}

	public void makeServer(File genDir, File srcDir, Marshals marshals) {
		File output = new File(genDir, namespace.replace(".", File.separator));
		try (PrintStream ps = FileOperation.fopen(output, "_" + name + ".java")) {
			ps.println();
			ps.println("package " + namespace + ";");
			ps.println();
			boolean needarraylist = view.getLifecycle() != ViewLifecycle.global && tablemap.getTableBinds().size() > 0;
			boolean needhashmap = view.getLifecycle() == ViewLifecycle.temporary;
			boolean needconcurrenthashmap = !view.getBinds().isEmpty();
			boolean needset = needconcurrenthashmap;
			boolean needmap = needhashmap || needconcurrenthashmap;
			if (needarraylist)
				ps.println("import java.util.ArrayList;");
			if (needhashmap)
				ps.println("import java.util.HashMap;");
			if (needset)
				ps.println("import java.util.HashSet;");
			if (needarraylist)
				ps.println("import java.util.List;");
			if (needmap)
				ps.println("import java.util.Map;");
			if (needset)
				ps.println("import java.util.Set;");
			if (needconcurrenthashmap)
				ps.println("import java.util.concurrent.ConcurrentHashMap;");
			ps.println("import limax.codec.OctetsStream;");
			ps.println("import limax.util.Resource;");
			ps.println("import limax.provider." + getViewParentClass() + ";");
			if (!(view.getBinds().isEmpty() && view.getVariables().isEmpty())) {
				ps.println("import limax.provider.ViewDataCollector;");
				if (!getProviderNamespace().equalsIgnoreCase(namespace))
					ps.println("import " + getProviderNamespace() + ".Marshals;");
			}
			ps.println(view.getComment());
			ps.println("public abstract class _" + name + " extends " + getViewParentClass() + " {");
			printServerDefine(ps, marshals);
			ps.println("}");
			ps.println();
		}
		output = new File(srcDir, namespace.replace(".", File.separator));
		if (new File(output, name + ".java").isFile())
			return;
		try (PrintStream ps = FileOperation.fopen(output, name + ".java")) {
			ps.println();
			ps.println("package " + namespace + ";");
			ps.println();
			if (view.getLifecycle() == ViewLifecycle.temporary)
				ps.println("import limax.provider.TemporaryView.Membership.AbortReason;");
			ps.println("import limax.provider." + getViewParentClass() + ";");
			ps.println();
			ps.println("public final class " + name + " extends " + namespace + "._" + name + " {");
			ps.println();
			ps.println("	private " + name + "(" + getViewParentClass() + ".CreateParameter param) {");
			ps.println("		super(param);");
			ps.println("		// bind here");
			ps.println("	}");
			ps.println();
			ps.println("	@Override");
			ps.println("	protected void onClose() {");
			ps.println("	}");
			ps.println();
			if (view.getLifecycle() == ViewLifecycle.temporary) {
				ps.println("	@Override");
				ps.println("	protected void onAttachAbort(long sessionid, AbortReason reason) {");
				ps.println("	}");
				ps.println();
				ps.println("	@Override");
				ps.println("	protected void onDetachAbort(long sessionid, AbortReason reason) {");
				ps.println("	}");
				ps.println();
				ps.println("	@Override");
				ps.println("	protected void onAttached(long sessionid) {");
				ps.println("	}");
				ps.println();
				ps.println("	@Override");
				ps.println("	protected void onDetached(long sessionid, byte reason) {");
				ps.println("		if (reason >= 0) {");
				ps.println("			//Application reason");
				ps.println("		} else {");
				ps.println("			//Connection abort reason");
				ps.println("		}");
				ps.println("	}");
				ps.println();
			}
			for (Control control : view.getControls()) {
				ps.println("	@Override");
				ps.println(
						"	protected void onControl(_" + name + "." + control.getName() + " param, long sessionid) {");
				ps.println("		// control implement");
				ps.println("	}");
				ps.println();
			}
			ps.println("	@Override");
			ps.println("	protected void onMessage(String message, long sessionid) {");
			ps.println("	}");
			ps.println();
			ps.println("}");
			ps.println();
		}
	}

	private int getSubscribeKey(Subscribe ref) {
		ViewFormatter refFormatter = viewgen.getViewFormatter(ref.getView());
		return (refFormatter.index << 16)
				| refFormatter.varnameindex.getIndex(ref.getBind() != null ? ref.getBind() : ref.getVariable());
	}

	private void printServerDefine(PrintStream ps, Marshals marshals) {
		if (view.getLifecycle() == ViewLifecycle.global)
			ps.println("	private final static String[] _varnames_ = new String[] {"
					+ Stream.concat(view.getVariables().stream(), view.getBinds().stream())
							.map(i -> "\"" + i.getName() + "\"").collect(Collectors.joining(","))
					+ "};");
		ps.println(
				Main.scriptSupport
						? "	private final static String[] _prefix_ = new String[] {" + Arrays
								.asList(view.getVariables().stream(), view.getBinds().stream(),
										view.getSubscribes().stream())
								.stream().flatMap(s -> s)
								.map(i -> "\"?" + StringStream.pack((Integer) i.attachment()) + "?\"")
								.collect(Collectors.joining(",")) + "};"
						: "	private final static String[] _prefix_ = new String["
								+ (view.getVariables().size() + view.getBinds().size() + view.getSubscribes().size())
								+ "];");
		if (view.getLifecycle() == ViewLifecycle.temporary) {
			ps.println("	private final static Map<Short, Map<Byte, Byte>> _subscribes_ = new HashMap<>();");
			if (!view.getSubscribes().isEmpty()) {
				Map<Short, Map<Byte, Byte>> map = new TreeMap<>();
				view.getSubscribes().forEach(subs -> {
					int key = getSubscribeKey(subs);
					short classindex = (short) (key >> 16);
					Map<Byte, Byte> m = map.get(classindex);
					if (m == null)
						map.put(classindex, m = new TreeMap<>());
					m.put((byte) (key & 0xff), (byte) varnameindex.getIndex(subs));
				});
				ps.println("	static {");
				ps.println("		Map<Byte, Byte> map;");
				map.forEach((k, v) -> {
					ps.println("		_subscribes_.put((short)" + k + ", map = new HashMap<>());");
					v.forEach((k0, v0) -> ps.println("		map.put((byte)" + k0 + ", (byte)" + v0 + ");"));
				});
				ps.println("	}");
			}
		}
		if (view.getLifecycle() == ViewLifecycle.temporary)
			ps.println("	private final static byte[][] _subscribe_collectors_ = new byte[3][];");
		ps.println("	private final static byte[][] _collectors_ = new byte[3][];");
		ps.println("	static {");
		switch (view.getLifecycle()) {
		case global:
			ps.println("		_collectors_[0] = new byte[] {};");
			ps.println(
					"		_collectors_[1] = new byte[] {"
							+ Stream.concat(view.getVariables().stream(), view.getBinds().stream())
									.map(n -> "(byte)" + varnameindex.getIndex(n)).collect(Collectors.joining(","))
							+ "};");
			ps.println("		_collectors_[2] = new byte[] {};");
			break;
		case temporary:
			ps.println(
					"		_subscribe_collectors_[0] = new byte[] {"
							+ view.getSubscribes().stream().filter(subs -> !subs.isSnapshot())
									.map(n -> "(byte)" + varnameindex.getIndex(n)).collect(Collectors.joining(","))
							+ "};");
			ps.println(
					"		_subscribe_collectors_[1] = new byte[] {"
							+ view.getSubscribes().stream().filter(subs -> subs.isSnapshot())
									.filter(subs -> Objects.nonNull(subs.getVariable()) || subs.getBind().isImmutable())
									.map(n -> "(byte)" + varnameindex.getIndex(n)).collect(Collectors.joining(","))
							+ "};");
			ps.println(
					"		_subscribe_collectors_[2] = new byte[] {"
							+ view.getSubscribes().stream().filter(subs -> subs.isSnapshot())
									.filter(subs -> Objects.nonNull(subs.getBind()) && !subs.getBind().isImmutable())
									.map(n -> "(byte)" + varnameindex.getIndex(n)).collect(Collectors.joining(","))
							+ "};");
		case session:
			ps.println("		_collectors_[0] = new byte[] {" + Stream
					.concat(view.getVariables().stream().filter(var -> !var.isSnapshot()),
							view.getBinds().stream().filter(bind -> !bind.isSnapshot()))
					.map(n -> "(byte)" + varnameindex.getIndex(n)).collect(Collectors.joining(",")) + "};");
			ps.println("		_collectors_[1] = new byte[] {" + Stream
					.concat(view.getVariables().stream().filter(var -> var.isSnapshot()),
							view.getBinds().stream().filter(bind -> bind.isSnapshot() && bind.isImmutable()))
					.map(n -> "(byte)" + varnameindex.getIndex(n)).collect(Collectors.joining(",")) + "};");
			ps.println(
					"		_collectors_[2] = new byte[] {"
							+ view.getBinds().stream().filter(bind -> bind.isSnapshot() && !bind.isImmutable())
									.map(n -> "(byte)" + varnameindex.getIndex(n)).collect(Collectors.joining(","))
							+ "};");
		}
		ps.println("	}");
		ps.println();

		if (!view.getBinds().isEmpty())
			ps.println("	private final Resource resource;");
		tablemap.printMap(ps);
		tablemap.getTableBinds().forEach(tb -> {
			if (view.getLifecycle() != ViewLifecycle.global) {
				tb.getBinds().forEach(bind -> {
					ps.println("	private final static Map<Object, List<ViewDataCollector.Field>> _field_collector_"
							+ bind.getName() + "_ = new ConcurrentHashMap<>();");
					fieldcollector.add(bind);
				});
			} else if (tb.getValueType() instanceof Xbean && !tb.isFullBind()) {
				tb.getBinds().forEach(bind -> {
					ps.println("	private final static Map<Object, Boolean> _touch_" + bind.getName()
							+ "_ = new ConcurrentHashMap<>();");
					touch.add(bind);
				});
			}
		});
		view.getVariables().stream().filter(Variable::isClip).forEach(var -> {
			ps.println("	protected boolean permit" + StringUtils.upper1(var.getName()) + "("
					+ TypeName.getBoxingName(var.getType()) + " _t_) {");
			ps.println("		return true;");
			ps.println("	}");
			ps.println();
		});
		view.getBinds().stream().filter(Bind::isClip).forEach(bind -> {
			ps.println("	protected boolean permit" + StringUtils.upper1(bind.getName()) + "("
					+ getServerBindType(view, bind) + " _t_) {");
			ps.println("		return true;");
			ps.println("	}");
			ps.println();
		});
		tablemap.printBinds(ps);
		tablemap.printListeners(ps, marshals);

		view.getVariables().forEach(var -> {
			String typeName = TypeName.getBoxingName(var.getType());
			String varName = StringUtils.upper1(var.getName());
			ps.println("	public final void _set" + varName + "(" + typeName + " _p_) {");
			String space;
			if (var.isClip()) {
				ps.println("		if (permit" + varName + "(_p_)) {");
				space = "	";
			} else
				space = "";
			ps.println(space + "		ViewDataCollector.Data _v_ = new ViewDataCollector.Data(" + marshals.get(var)
					+ "(_p_), "
					+ (Main.scriptSupport ? "isScriptEnabled() ? " + marshals.get(var) + "(\"\", _p_) : \"\"" : "\"\"")
					+ ");");
			ps.println("		schedule(() -> update((byte)" + varnameindex.getIndex(var) + ", _v_));");
			if (var.isClip())
				ps.println("		}");
			ps.println("	}");
			ps.println();
			ps.println("	public final void set" + varName + "(" + typeName + " _p_) {");
			ps.println("		if (limax.zdb.Transaction.isActive())");
			ps.println(
					"			limax.provider.ProcedureHelper.executeWhileCommit(() -> _set" + varName + "(_p_));");
			ps.println("		else");
			ps.println("			_set" + varName + "(_p_);");
			ps.println("	}");
			ps.println();
		});

		view.getBinds().forEach(bind -> Setbind.make(ps, view, bind, this, marshals, true));
		if (view.getLifecycle() != ViewLifecycle.global)
			tablemap.getTableBinds().stream().forEach(tb -> {
				tb.getBinds().stream().filter(bind -> !bind.getInner().isImmutable())
						.forEach(bind -> Setbind.make(ps, view, bind.getInner(), this, marshals, false));
			});

		switch (view.getLifecycle()) {
		case temporary: {
			ps.println("	public static " + view.getName() + " getInstance(long sessionId, int instanceindex) {");
			ps.println("		return (" + view.getName() + ")" + nsprovider
					+ ".ViewManager.getViewContext().findTemporaryView(sessionId, (short)" + index
					+ ", instanceindex);");
			ps.println("	}");
			ps.println();
			ps.println("	private static " + view.getName() + " createInstance(boolean loose, int partition) {");
			ps.println("		return (" + view.getName() + ")" + nsprovider
					+ ".ViewManager.getViewContext().createTemporaryView((short)" + index + ", loose, partition);");
			ps.println("	}");
			ps.println();
			ps.println("	public static " + view.getName() + " createInstance() {");
			ps.println("		return createInstance(false, 1);");
			ps.println("	}");
			ps.println();
			ps.println("	public static " + view.getName() + " createInstance(int partition) {");
			ps.println("		return createInstance(false, partition);");
			ps.println("	}");
			ps.println();
			if (view.getSubscribes().isEmpty()) {
				ps.println("	public static " + view.getName() + " createLooseInstance() {");
				ps.println("		return createInstance(true, 1);");
				ps.println("	}");
				ps.println();
				ps.println("	public static " + view.getName() + " createLooseInstance(int partition) {");
				ps.println("		return createInstance(true, partition);");
				ps.println("	}");
				ps.println();
			}
			ps.println("	public void destroyInstance() {");
			ps.println("		" + nsprovider + ".ViewManager.getViewContext().closeTemporaryView(this);");
			ps.println("	}");
			break;
		}
		case session: {
			ps.println("	public static " + view.getName() + " getInstance(long sessionid) {");
			ps.println("		return (" + view.getName() + ")" + nsprovider
					+ ".ViewManager.getViewContext().findSessionView(sessionid, (short)" + index + ");");
			ps.println("	}");
			break;
		}
		case global: {
			ps.println("	public static " + view.getName() + " getInstance() {");
			ps.println("		return (" + view.getName() + ")" + nsprovider
					+ ".ViewManager.getViewContext().findGlobalView((short)" + index + ");");
			ps.println("	}");
			break;
		}
		}
		ps.println();

		Construct.make(view, view.getName(), ps, "	", true, true);

		ps.println("	@Override");
		ps.println(
				"	protected final void processControl(byte controlIndex, OctetsStream controlParameter, long sessionid) {");
		if (!view.getControls().isEmpty()) {
			ps.println("		try {");
			ps.println("			switch (controlIndex) {");
			view.getControls().forEach(control -> {
				ps.println("			case " + varnameindex.getIndex(control) + ":");
				ps.println("				_" + control.getFullName() + " " + control.getName().toLowerCase()
						+ " = new _" + control.getFullName() + "();");
				ps.println("				" + control.getName().toLowerCase() + ".unmarshal(controlParameter);");
				ps.println("				onControl(" + control.getName().toLowerCase() + ", sessionid);");
				ps.println("				break;");
			});
			ps.println("			default:");
			ps.println("				throw new RuntimeException(\"unknown control name\");");
			ps.println("			}");
			ps.println("		} catch (limax.codec.MarshalException e) {");
			ps.println("			throw new RuntimeException(e); ");
			ps.println("		}");
		}
		ps.println("	}");
		ps.println();

		view.getControls().forEach(control -> {
			ps.println("	protected abstract void onControl(_" + control.getFullName() + " param, long sessionid);");
			ps.println();
		});

		view.getBinds().forEach(bind -> new BindFormatter(bind, true).make(ps));
		view.getControls()
				.forEach(control -> new ControlFormatter(control, true, varnameindex.getIndex(control)).make(ps, "	"));
	}
}
