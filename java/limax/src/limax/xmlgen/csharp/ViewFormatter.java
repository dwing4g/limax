package limax.xmlgen.csharp;

import java.io.File;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import limax.provider.ViewLifecycle;
import limax.util.StringUtils;
import limax.xmlgen.Bind;
import limax.xmlgen.FileOperation;
import limax.xmlgen.NameStringToIndex;
import limax.xmlgen.Subscribe;
import limax.xmlgen.Type;
import limax.xmlgen.TypeList;
import limax.xmlgen.TypeMap;
import limax.xmlgen.TypeSet;
import limax.xmlgen.TypeVector;
import limax.xmlgen.Variable;
import limax.xmlgen.View;
import limax.xmlgen.Xbean;

public class ViewFormatter {
	private final View view;
	private final Viewgen viewgen;
	private final int index;
	private final String namespace;
	private final NameStringToIndex nameindex;

	public ViewFormatter(Viewgen viewgen, View view, int index) {
		this.view = view;
		this.viewgen = viewgen;
		this.namespace = viewgen.makeViewNameSpace(view);
		this.index = index;
		this.nameindex = view.getMemberNameStringToIndex();
	}

	static private final String getClassPerfixWords(boolean isTemp) {
		if (isTemp)
			return "public sealed partial class";
		else
			return "public sealed class";
	}

	String getNameSpace() {
		return namespace;
	}

	private boolean hasVariables() {
		return view.getVariables().size() > 0 || view.getBinds().size() > 0 || view.getSubscribes().size() > 0;
	}

	public void make(File outputGen, File outputSrc) {
		final String name = view.getLastName();
		final boolean isTemp = ViewLifecycle.temporary == view.getLifecycle();
		try (final PrintStream ps = FileOperation.fopen(outputGen, view.getFullName() + ".cs")) {
			BeanFormatter.printCommonInclude(ps);
			ps.println("using limax.endpoint;");
			ps.println("namespace " + namespace);
			ps.println("{");
			ps.println("	" + getClassPerfixWords(isTemp) + " " + name + " : " + (isTemp ? "TemporaryView" : "View"));
			ps.println("	{");
			printDefine(ps, isTemp);
			ps.println("	}");
			ps.println("}");
		}
		if (!isTemp)
			return;
		if (new File(outputSrc, view.getFullName() + ".cs").isFile())
			return;
		try (final PrintStream ps = FileOperation.fopen(outputSrc, view.getFullName() + ".cs")) {
			BeanFormatter.printCommonInclude(ps);
			ps.println("namespace " + namespace);
			ps.println("{");
			ps.println("	public sealed partial class " + name);
			ps.println("	{");
			ps.println("		override protected void onClose() {}");
			ps.println("		override protected void onAttach(long sessionid) {}");
			ps.println("		override protected void onDetach(long sessionid, byte reason) {}");
			ps.println("		override protected void onOpen(ICollection<long> sessionids) {}");
			ps.println("	}");
			ps.println("}");
		}
	}

	private String getSubscribeTypeName(Subscribe ref) {
		if (ref.getVariable() != null)
			return TypeName.getName(ref.getVariable().getType());
		Bind bind = ref.getBind();
		return bind.isFullBind() ? TypeName.getName(bind.getValueType())
				: viewgen.getViewFormatter(ref.getView()).namespace + "." + ref.getView().getName() + ".__"
						+ bind.getName();
	}

	private String getBindTypeName(Bind bind) {
		return bind.isFullBind() ? TypeName.getName(bind.getValueType())
				: view.getName() + ".__" + bind.getName();
	}

	private void printViewChangeType(PrintStream ps, View view, int start) {
		ps.println("			bool __o_flags__;");
		if (view.getSubscribes().isEmpty()) {
			ps.println("			__o_flags__ = __flags__.get(index);");
			ps.println("			__flags__.set(index);");
		} else {
			ps.println("			if (index < " + start + ")");
			ps.println("			{");
			ps.println("				__o_flags__ = __flags__.get(index);");
			ps.println("				__flags__.set(index);");
			ps.println("			}");
			ps.println("			else");
			ps.println("			{");
			ps.println("				__o_flags__ = __subsFlags__.get(sessionid, index - " + start + ");");
			ps.println("				__subsFlags__.set(sessionid, index - " + start + ");");
			ps.println("			}");
		}
		ps.println("			ViewChangedType _t_ = __o_flags__ ? ViewChangedType.REPLACE : ViewChangedType.NEW;");
	}

	private void printXbeanBindUpdate(PrintStream ps, Bind bind) {
		AtomicInteger i = new AtomicInteger();
		ps.println("					switch(field)");
		ps.println("					{");
		(bind.isFullBind() ? ((Xbean) bind.getValueType()).getVariables() : bind.getVariables())
				.forEach(var -> {
					ps.println("						case " + i.getAndIncrement() + ":");
					ps.println("						{");
					Type type = var.getType();
					String field = "_s_." + var.getName();
					if (type instanceof TypeMap) {
						Unmarshal.make(type, field, ps, "							");
						ps.println("							_os_ = OctetsStream.wrap(dataremoved);");
						ConstructWithUnmarshal.make(((TypeMap) type).getKeySetType().asTypeVector(), "_r_", ps,
								"							");
						ps.println("							foreach(var _i_ in _r_) " + field + ".Remove(_i_);");
					} else if (type instanceof TypeSet) {
						Unmarshal.make(type, field, ps, "							");
						ps.println("							_os_ = OctetsStream.wrap(dataremoved);");
						ConstructWithUnmarshal.make(((TypeSet) type).asTypeVector(), "_r_", ps,
								"							");
						ps.println("							foreach(var _i_ in _r_) " + field + ".Remove(_i_);");
					} else {
						if (type instanceof TypeVector || type instanceof TypeList)
							ps.println("							" + field + ".Clear();");
						Unmarshal.make(type, field, ps, "							");
					}
					ps.println("							break;");
					ps.println("						}");
				});
		ps.println("					}");
	}

	public void printDefine(PrintStream ps, boolean isTemp) {
		int start = view.getVariables().size() + view.getBinds().size();
		ps.println("		private static int __pvid__;");
		if (hasVariables()) {
			ps.println("		private readonly limax.util.BitSet __flags__ = new limax.util.BitSet(" + start + ");");
			if (!view.getSubscribes().isEmpty())
				ps.println(
						"		private readonly limax.util.MapBitSet<long> __subsFlags__ = new limax.util.MapBitSet<long>("
								+ view.getSubscribes().size() + ");");
			ps.println("		private ViewChangedType __type__ = ViewChangedType.TOUCH;");
		}
		BeanFormatter.declareEnums(ps, view.getEnums());

		Define.make(view, ps, "		");

		view.getSubscribes().forEach(subs -> {
			String type = getSubscribeTypeName(subs);
			ps.println("		private Dictionary<long, " + type + "> " + subs.getName() + " = new Dictionary<long, "
					+ type + ">();");
		});

		ps.println("		internal " + view.getLastName()
				+ "(ViewContext vc) : base(vc) { __pvid__ = vc.getProviderId(); }");

		view.getVariables().forEach(var -> {
			ps.println("		public void visit" + StringUtils.upper1(var.getName()) + "(ViewVisitor<"
					+ TypeName.getName(var.getType()) + "> v) { lock(this) { if(__flags__.get("
					+ nameindex.getIndex(var) + ")) v(" + var.getName() + "); } }");
		});
		view.getBinds().forEach(bind -> {
			ps.println("		public void visit" + StringUtils.upper1(bind.getName()) + "(ViewVisitor<"
					+ getBindTypeName(bind) + "> v) { lock(this) { if(__flags__.get(" + nameindex.getIndex(bind)
					+ ")) v(" + bind.getName() + "); } }");
		});
		view.getSubscribes().forEach(subs -> {
			ps.println(
					"		public void visit" + StringUtils.upper1(subs.getName()) + "(ViewVisitor<IDictionary<long, "
							+ getSubscribeTypeName(subs) + ">> v) { lock(this) { v(" + subs.getName() + "); } }");
		});
		// override getName
		ps.println("		override public short getClassIndex()");
		ps.println("		{");
		ps.println("			return " + index + ";");
		ps.println("		}");

		if (view.getLifecycle() == ViewLifecycle.temporary) {
			ps.println("		protected sealed override void detach(long sessionid, byte reason) {");
			ps.println("			onDetach(sessionid, reason);");
			boolean f = false;
			for (final Subscribe subs : view.getSubscribes()) {
				ps.println("			this." + subs.getName() + ".Remove(sessionid);");
				f = true;
			}
			if (f)
				ps.println("			this.__subsFlags__.remove(sessionid);");
			ps.println("		}");
		}
		ps.println(
				"		protected override void onData(long sessionid, byte index, byte field, Octets data, Octets dataremoved)");
		ps.println("		{");
		if (hasVariables()) {
			ps.println("			if ((index & 0x80) == 0x80)");
			ps.println("				onRemoved(sessionid, (byte) (index & 0x7f));");
			ps.println("			else if (data.size() == 0)");
			ps.println("				onChanged(sessionid, index);");
			ps.println("			else if ((field & 0x80) == 0x80)");
			ps.println("				onChanged(sessionid, index, data);");
			ps.println("			else");
			ps.println("				onChanged(sessionid, index, field, data, dataremoved);");
		}
		ps.println("		}");

		if (hasVariables()) {
			ps.println(
					"		private void onChanged(long sessionid, byte index, byte field, Octets data, Octets dataremoved)");
			ps.println("		{");
			if (Stream
					.concat(view.getBinds().stream().filter(bind -> bind.getValueType() instanceof Xbean)
							.map(bind -> (Xbean) bind.getValueType()),
							view.getSubscribes().stream().filter(subs -> subs.getBind() != null)
									.filter(subs -> subs.getBind().getValueType() instanceof Xbean)
									.map(subs -> (Xbean) subs.getBind().getValueType()))
					.findAny().isPresent()) {
				printViewChangeType(ps, view, start);
				ps.println("			OctetsStream _os_ = OctetsStream.wrap(data);");
				ps.println("			switch(index)");
				ps.println("			{");
				view.getBinds().stream().filter(bind -> bind.getValueType() instanceof Xbean)
						.forEach(bind -> {
							ps.println("				case " + nameindex.getIndex(bind) + ":");
							ps.println("				{");
							Xbean xbean = (Xbean) bind.getValueType();
							String type = (bind.isFullBind() ? TypeName.getName(xbean) : "__" + bind.getName());
							String self = bind.getName();
							ps.println("					" + type + " _s_ = _t_ == ViewChangedType.REPLACE ? " + self
									+ " : (" + self + " = new " + type + "());");
							printXbeanBindUpdate(ps, bind);
							ps.println("					__type__ = _t_;");
							ps.println("					break;");
							ps.println("				}");
						});
				view.getSubscribes().stream().filter(subs -> subs.getBind() != null)
						.filter(subs -> subs.getBind().getValueType() instanceof Xbean).forEach(subs -> {
							ps.println("				case " + nameindex.getIndex(subs) + ":");
							ps.println("				{");
							ps.println("					" + getSubscribeTypeName(subs) + " _s_;");
							ps.println("					if (_t_ == ViewChangedType.REPLACE)");
							ps.println(
									"						" + subs.getName() + ".TryGetValue(sessionid, out _s_);");
							ps.println("					else");
							ps.println("						" + subs.getName() + ".Add(sessionid, _s_ = new "
									+ getSubscribeTypeName(subs) + "());");
							printXbeanBindUpdate(ps, subs.getBind());
							ps.println("					__type__ = _t_;");
							ps.println("					break;");
							ps.println("				}");

						});
				ps.println("				default:");
				ps.println(
						"					throw new Exception( \"view \\\"\" + this + \"\\\" mismatch bind index = \\\"\" + index + \"\\\"\");");
				ps.println("			}");
			}
			ps.println("		}");

			ps.println("		private void onChanged(long sessionid, byte index, Octets data)");
			ps.println("		{");
			printViewChangeType(ps, view, start);
			ps.println("			OctetsStream _os_ = OctetsStream.wrap(data);");
			ps.println("			switch(index)");
			ps.println("			{");
			view.getVariables().forEach(var -> {
				ps.println("				case " + nameindex.getIndex(var) + ":");
				ps.println("				{");
				ConstructWithUnmarshal.make(var.getType(), "_n_", ps, "					");
				ps.println("					" + var.getName() + " = _n_;");
				ps.println("					onViewChanged(sessionid, \"" + var.getName() + "\", _n_, _t_);");
				ps.println("					break;");
				ps.println("				}");
			});
			view.getBinds().forEach(bind -> {
				ps.println("				case " + nameindex.getIndex(bind) + ":");
				ps.println("				{");
				if (bind.isFullBind()) {
					ConstructWithUnmarshal.make(bind.getValueType(), "_n_", ps, "					");
				} else {
					ConstructWithUnmarshal.make("__" + bind.getName(), "_n_", ps, "					");
				}
				ps.println("					" + bind.getName() + " = _n_;");
				ps.println("					onViewChanged(sessionid, \"" + bind.getName() + "\", _n_, _t_);");
				ps.println("					break;");
				ps.println("				}");
			});
			view.getSubscribes().forEach(subs -> {
				ps.println("				case " + nameindex.getIndex(subs) + ":");
				ps.println("				{");
				final Variable var = subs.getVariable();
				if (var != null) {
					ConstructWithUnmarshal.make(var.getType(), "_n_", ps, "					");
				} else {
					Bind bind = subs.getBind();
					if (bind.isFullBind()) {
						ConstructWithUnmarshal.make(bind.getValueType(), "_n_", ps, "					");
					} else {
						ConstructWithUnmarshal.make(getSubscribeTypeName(subs), "_n_", ps, "					");
					}
				}
				ps.println("					" + subs.getName() + ".Remove(sessionid);");
				ps.println("					" + subs.getName() + ".Add(sessionid, _n_);");
				ps.println("					onViewChanged(sessionid, \"" + subs.getName() + "\", _n_, _t_);");
				ps.println("					break;");
				ps.println("				}");
			});
			ps.println("				default:");
			ps.println(
					"					throw new Exception( \"view \\\"\" + this + \"\\\" lost var index = \\\"\" + index + \"\\\"\");");
			ps.println("			}");
			ps.println("		}");

			ps.println("		private void onChanged(long sessionid, byte index, ViewChangedType type)");
			ps.println("		{");
			ps.println("			switch(index)");
			ps.println("			{");
			Stream.concat(view.getVariables().stream(), view.getBinds().stream()).forEach(var -> {
				ps.println("				case " + nameindex.getIndex(var) + ":");
				ps.println("					onViewChanged(sessionid, \"" + var.getName() + "\", " + var.getName()
						+ ", type);");
				ps.println("					break;");
			});
			view.getSubscribes().forEach(subs -> {
				ps.println("				case " + nameindex.getIndex(subs) + ": {");
				ps.println("					" + getSubscribeTypeName(subs) + " _o_;");
				ps.println("					" + subs.getName() + ".TryGetValue(sessionid, out _o_);");
				ps.println("					onViewChanged(sessionid, \"" + subs.getName() + "\", _o_, type);");
				ps.println("					break;");
				ps.println("				}");
			});
			ps.println("				default:");
			ps.println(
					"					throw new Exception( \"view \\\"\" + this + \"\\\" lost var index = \\\"\" + index + \"\\\"\");");
			ps.println("			}");
			ps.println("		}");

			ps.println("		private void onChanged(long sessionid, byte index)");
			ps.println("		{");
			ps.println("			onChanged(sessionid, index, __type__);");
			ps.println("			__type__ = ViewChangedType.TOUCH;");
			ps.println("		}");

			ps.println("		private void onRemoved(long sessionid, byte index) {");
			if (view.getSubscribes().isEmpty()) {
				ps.println("			__flags__.clear(index);");
			} else {
				ps.println("			if (index < " + start + ")");
				ps.println("				__flags__.clear(index);");
				ps.println("			else");
				ps.println("				__subsFlags__.clear(sessionid, index - " + start + ");");
			}
			ps.println("			onChanged(sessionid, index, ViewChangedType.DELETE);");
			ps.println("		}");
		}

		final String fieldnamesset = "_fieldnames_" + view.getName();
		ps.println("		static private ISet<string> " + fieldnamesset + " = new HashSet<string>();");
		ps.println("		static " + view.getName() + "()");
		ps.println("		{");
		Stream.concat(Stream.concat(view.getVariables().stream(), view.getBinds().stream()),
				view.getSubscribes().stream())
				.forEach(var -> ps.println("			" + fieldnamesset + ".Add(\"" + var.getName() + "\");"));
		ps.println("		}");

		ps.println("		protected override ISet<string> getFieldNames()");
		ps.println("		{");
		ps.println("			return " + fieldnamesset + ";");
		ps.println("		}");
		if (ViewLifecycle.temporary == view.getLifecycle()) {
			ps.println("		public static " + view.getName() + " getInstance(int instanceindex) {");
			ps.println("			return getInstance(Endpoint.getDefaultEndpointManager(), instanceindex);");
			ps.println("		}");
			ps.println("		public static " + view.getName()
					+ " getInstance(EndpointManager manager, int instanceindex) {");
			ps.println("			return getInstance(manager, __pvid__, instanceindex);");
			ps.println("		}");
			ps.println("		public static " + view.getName()
					+ " getInstance(EndpointManager manager, int pvid, int instanceindex) {");
			ps.println("			ViewContext vc = " + "manager.getViewContext(pvid, ViewContextKind.Static);");
			ps.println("			return vc == null ? null : (" + view.getName() + ")vc.findTemporaryView((short)"
					+ index + ", instanceindex);");
			ps.println("	}");
		} else {
			ps.println("		public static " + view.getName() + " getInstance() {");
			ps.println("			return getInstance(Endpoint.getDefaultEndpointManager());");
			ps.println("		}");
			ps.println("		public static " + view.getName() + " getInstance(EndpointManager manager) {");
			ps.println("			return getInstance(manager, __pvid__);");
			ps.println("		}");
			ps.println("		public static " + view.getName() + " getInstance(EndpointManager manager, int pvid) {");
			ps.println("			ViewContext vc = " + "manager.getViewContext(pvid, ViewContextKind.Static);");
			ps.println("			return vc == null ? null : (" + view.getName()
					+ ")vc.getSessionOrGlobalView((short)" + index + ");");
			ps.println("		}");
		}
		view.getBinds().forEach(bind -> new BindFormatter(bind).make(ps));
		view.getControls()
				.forEach(control -> new ControlFormatter(control, index, nameindex.getIndex(control)).make(ps));
	}
}
