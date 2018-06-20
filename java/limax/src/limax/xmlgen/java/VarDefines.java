package limax.xmlgen.java;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import limax.provider.ViewLifecycle;
import limax.util.Pair;
import limax.xmlgen.Bean;
import limax.xmlgen.Bind;
import limax.xmlgen.Cbean;
import limax.xmlgen.Main;
import limax.xmlgen.Subscribe;
import limax.xmlgen.Type;
import limax.xmlgen.TypeAny;
import limax.xmlgen.TypeBinary;
import limax.xmlgen.TypeBoolean;
import limax.xmlgen.TypeByte;
import limax.xmlgen.TypeDouble;
import limax.xmlgen.TypeFloat;
import limax.xmlgen.TypeInt;
import limax.xmlgen.TypeList;
import limax.xmlgen.TypeLong;
import limax.xmlgen.TypeMap;
import limax.xmlgen.TypeSet;
import limax.xmlgen.TypeShort;
import limax.xmlgen.TypeString;
import limax.xmlgen.TypeVector;
import limax.xmlgen.Variable;
import limax.xmlgen.View;
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;

final class VarDefines {
	private final static int SPLICE_NAMEDICT_LIMIT = 16;

	private final static class TypeStr {

		TypeStr(String t, String k, String v, int tdv) {
			typeString = t;
			typeKeyString = k;
			typeValueString = v;
			typeDependencyValue = tdv;
		}

		TypeStr(String t, int tdv) {
			typeString = t;
			typeKeyString = "0";
			typeValueString = "0";
			typeDependencyValue = tdv;
		}

		final String typeString;
		final String typeKeyString;
		final String typeValueString;

		final int typeDependencyValue;

		String getPrintString() {
			return typeString + ", " + typeKeyString + ", " + typeValueString;
		}
	}

	private static class BeanVarInfo {
		final int name;
		final TypeStr type;

		BeanVarInfo(int name, TypeStr type) {
			this.name = name;
			this.type = type;
		}

		String getPrintString() {
			return "new limax.defines.VariantVariableDefine(" + name + ", " + type.getPrintString() + ")";
		}
	}

	private static class ViewVarInfo {
		final int name;
		final TypeStr type;
		final boolean isbind;

		ViewVarInfo(int name, TypeStr type, boolean isbind) {
			this.name = name;
			this.type = type;
			this.isbind = isbind;
		}

		String getPrintString() {
			return "new limax.defines.VariantViewVariableDefine(" + name + ", " + type.getPrintString() + ", "
					+ Boolean.toString(isbind) + ")";
		}
	}

	private static class ViewCtrlInfo {
		final int name;
		final Collection<BeanVarInfo> vars = new ArrayList<>();

		public ViewCtrlInfo(int name) {
			this.name = name;
		}
	}

	private static class ViewInfo {
		final Collection<Integer> name;
		final int clsindex;
		final boolean istemp;

		final Collection<ViewVarInfo> vars = new ArrayList<>();
		final Collection<ViewVarInfo> subs = new ArrayList<>();
		final Collection<ViewCtrlInfo> ctrls = new ArrayList<>();

		ViewInfo(Collection<Integer> name, int clsindex, boolean istemp) {
			this.name = name;
			this.clsindex = clsindex;
			this.istemp = istemp;
		}
	}

	private static class BeanInfo {
		final Collection<BeanVarInfo> vars = new ArrayList<>();
		final int type;

		public BeanInfo(int type) {
			this.type = type;
		}

		public int getTypeDependencyValue() {
			return type + getSortDependencyValue();
		}

		public int getSortDependencyValue() {
			return vars.stream().collect(Collectors.summingInt(v -> v.type.typeDependencyValue));
		}
	}

	private static class SubscribeInfo {
		final TypeStr type;
		final boolean isbind;

		public SubscribeInfo(TypeStr type, boolean isbind) {
			this.type = type;
			this.isbind = isbind;
		}
	}

	private final class TypeIds {
		private int typeidgen = 32; // VariantDefines.BASE_TYPE_MAX;

		private final List<BeanInfo> infos = new ArrayList<>();
		private final Map<String, BeanInfo> idmap = new HashMap<>();

		BeanVarInfo createVarInfo(Variable var) {
			return new BeanVarInfo(addVarName(var.getName()), getTypeString(var.getType()));
		}

		Collection<BeanInfo> getBeanInfos() {
			infos.sort((a, b) -> a.getSortDependencyValue() - b.getSortDependencyValue());
			return infos;
		}

		TypeStr getBeanType(String fullname, Collection<Variable> vars) {
			BeanInfo info = idmap.get(fullname);
			if (null == info) {
				info = new BeanInfo(++typeidgen);
				idmap.put(fullname, info);
				infos.add(info);
				for (Variable v : vars)
					info.vars.add(createVarInfo(v));
				hasBeanVariable = hasBeanVariable || !info.vars.isEmpty();
			}
			return new TypeStr(Integer.toString(info.type), info.getTypeDependencyValue());
		}

		TypeStr getBindType(String fullname, Bind bind) {
			if (bind.isFullBind())
				return getTypeString(bind.getValueType());
			else
				return getBeanType(fullname, bind.getVariables());
		}

		TypeStr getTypeString(Type type) {
			TypeIndex ti = new TypeIndex();
			type.accept(ti);
			return ti.typeString;
		}

		SubscribeInfo getSubscribeInfo(String viewfullname, Subscribe sub) {
			final Bind bind = sub.getBind();
			if (null != bind) {
				boolean isbind = bind.getValueType() instanceof Xbean;
				return new SubscribeInfo(getBindType(viewfullname + "." + sub.getName(), bind), isbind);
			} else
				return new SubscribeInfo(getTypeString(sub.getVariable().getType()), false);
		}

		private final class TypeIndex implements Visitor {

			TypeStr typeString;

			@Override
			public void visit(TypeBoolean type) {
				typeString = new TypeStr("VariantDefines.BASE_TYPE_BOOLEAN", 0);
			}

			@Override
			public void visit(TypeByte type) {
				typeString = new TypeStr("VariantDefines.BASE_TYPE_BYTE", 0);
			}

			@Override
			public void visit(TypeShort type) {
				typeString = new TypeStr("VariantDefines.BASE_TYPE_SHORT", 0);
			}

			@Override
			public void visit(TypeInt type) {
				typeString = new TypeStr("VariantDefines.BASE_TYPE_INT", 0);
			}

			@Override
			public void visit(TypeLong type) {
				typeString = new TypeStr("VariantDefines.BASE_TYPE_LONG", 0);
			}

			@Override
			public void visit(TypeFloat type) {
				typeString = new TypeStr("VariantDefines.BASE_TYPE_FLOAT", 0);
			}

			@Override
			public void visit(TypeDouble type) {
				typeString = new TypeStr("VariantDefines.BASE_TYPE_DOUBLE", 0);
			}

			@Override
			public void visit(TypeBinary type) {
				typeString = new TypeStr("VariantDefines.BASE_TYPE_BINARY", 0);
			}

			@Override
			public void visit(TypeString type) {
				typeString = new TypeStr("VariantDefines.BASE_TYPE_STRING", 0);
			}

			@Override
			public void visit(TypeList type) {
				TypeStr value = getTypeString(type.getValueType());
				typeString = new TypeStr("VariantDefines.BASE_TYPE_LIST", "0", value.typeString,
						value.typeDependencyValue);
			}

			@Override
			public void visit(TypeSet type) {
				TypeStr value = getTypeString(type.getValueType());
				typeString = new TypeStr("VariantDefines.BASE_TYPE_SET", "0", value.typeString,
						value.typeDependencyValue);
			}

			@Override
			public void visit(TypeVector type) {
				TypeStr value = getTypeString(type.getValueType());
				typeString = new TypeStr("VariantDefines.BASE_TYPE_VECTOR", "0", value.typeString,
						value.typeDependencyValue);
			}

			@Override
			public void visit(TypeMap type) {
				TypeStr key = getTypeString(type.getKeyType());
				TypeStr value = getTypeString(type.getValueType());
				typeString = new TypeStr("VariantDefines.BASE_TYPE_MAP", key.typeString, value.typeString,
						key.typeDependencyValue + value.typeDependencyValue);
			}

			@Override
			public void visit(Bean type) {
				TypeStr bean = getBeanType(type.getFullName(), type.getVariables());
				typeString = new TypeStr(bean.typeString, bean.typeDependencyValue);
			}

			@Override
			public void visit(Cbean type) {
				TypeStr bean = getBeanType(type.getFullName(), type.getVariables());
				typeString = new TypeStr(bean.typeString, bean.typeDependencyValue);
			}

			@Override
			public void visit(Xbean type) {
				TypeStr bean = getBeanType(type.getFullName(), type.getVariables());
				typeString = new TypeStr(bean.typeString, bean.typeDependencyValue);
			}

			@Override
			public void visit(TypeAny type) {
				throw new UnsupportedOperationException();
			}
		}
	}

	private final Viewgen viewgen;

	private int nameidgen = 0;

	private final Map<String, Integer> namedict = new HashMap<>();
	private final TypeIds typeids = new TypeIds();
	private final Collection<ViewInfo> views;

	private boolean hasViewControl = false;
	private boolean hasBeanVariable = false;
	private boolean hasViewVariableOrSubscribe = false;

	VarDefines(Viewgen viewgen) {
		this.viewgen = viewgen;
		views = Main.variantSupport
				? viewgen.getViews().stream().map(view -> createViewInfo(view)).collect(Collectors.toList()) : null;
	}

	private int addName(String name) {
		Integer id = namedict.get(name);
		if (null != id)
			return id;
		int nid = ++nameidgen;
		namedict.put(name, nid);
		return nid;
	}

	private Collection<Integer> addViewName(String name) {
		Collection<Integer> ids = new ArrayList<>();
		String[] ns = name.split("\\.");
		for (String n : ns)
			ids.add(addName(n));
		return ids;
	}

	private int addVarName(String name) {
		return addName(name);
	}

	private ViewInfo createViewInfo(View view) {
		final String viewFullName = view.getFullName();
		final ViewInfo viewinfo = new ViewInfo(addViewName(viewFullName), viewgen.getViewIndex(view),
				ViewLifecycle.temporary == view.getLifecycle());
		view.getVariables().forEach(var -> viewinfo.vars
				.add(new ViewVarInfo(addVarName(var.getName()), typeids.getTypeString(var.getType()), false)));
		view.getBinds().forEach(bind -> viewinfo.vars.add(new ViewVarInfo(addVarName(bind.getName()),
				typeids.getBindType(viewFullName + "." + bind.getName(), bind), true)));
		if (ViewLifecycle.temporary == view.getLifecycle())
			view.getSubscribes().forEach(sub -> {
				final SubscribeInfo subinfo = typeids.getSubscribeInfo(sub.getView().getFullName(), sub);
				viewinfo.subs.add(new ViewVarInfo(addVarName(sub.getName()), subinfo.type, subinfo.isbind));
			});
		view.getControls().forEach(ctrl -> {
			final ViewCtrlInfo info = new ViewCtrlInfo(addVarName(ctrl.getName()));
			ctrl.getVairables().forEach(var -> info.vars.add(typeids.createVarInfo(var)));
			viewinfo.ctrls.add(info);
			hasBeanVariable = hasBeanVariable || !info.vars.isEmpty();
		});
		hasViewControl = hasViewControl || !viewinfo.ctrls.isEmpty();
		hasViewVariableOrSubscribe = hasViewVariableOrSubscribe || !view.getVariables().isEmpty()
				|| !view.getBinds().isEmpty();
		return viewinfo;
	}

	private void printView(ViewInfo viewinfo, PrintStream ps) {
		ps.println("{");
		ps.println("		limax.defines.VariantViewDefine viewdef = new limax.defines.VariantViewDefine();");
		viewinfo.name.forEach(id -> ps.println("		viewdef.name.ids.add(" + id + ");"));
		ps.println("		viewdef.clsindex = (short)" + viewinfo.clsindex + ";");
		ps.println("		viewdef.istemp = " + viewinfo.istemp + ";");
		viewinfo.vars.forEach(var -> ps.println("		viewdef.vars.add(" + var.getPrintString() + ");"));
		viewinfo.subs.forEach(sub -> ps.println("		viewdef.subs.add(" + sub.getPrintString() + ");"));
		viewinfo.ctrls.forEach(ctrl -> {
			ps.println("		{");
			ps.println(
					"			limax.defines.VariantViewControlDefine cd = new limax.defines.VariantViewControlDefine();");
			ps.println("			cd.name = " + ctrl.name + ";");
			ctrl.vars.forEach(var -> ps.println("			cd.vars.add(" + var.getPrintString() + ");"));
			ps.println("			viewdef.ctrls.add(cd);");
			ps.println("		}");
		});
		ps.println("		defines.views.add(viewdef);");
		ps.println("	}");
	}

	private void printBean(BeanInfo beaninfo, PrintStream ps) {
		ps.println("{");
		ps.println("		limax.defines.VariantBeanDefine beandef = new limax.defines.VariantBeanDefine();");
		ps.println("		beandef.type = " + beaninfo.type + ";");
		beaninfo.vars.forEach(var -> ps.println("		beandef.vars.add(" + var.getPrintString() + ");"));
		ps.println("		defines.beans.add(beandef);");
		ps.println("	}");
	}

	private String printViewMethodBody(ViewInfo viewinfo) {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			printView(viewinfo, new PrintStream(baos));
			return new String(baos.toByteArray());
		} catch (IOException e) {
			return "";
		}
	}

	private String printBeanMethodBody(BeanInfo beaninfo) {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			printBean(beaninfo, new PrintStream(baos));
			return new String(baos.toByteArray());
		} catch (IOException e) {
			return "";
		}
	}

	private List<String> printNameDict() {
		List<String> r = new ArrayList<>();
		StringBuilder sb = null;
		int count = 0;
		for (Map.Entry<String, Integer> e : namedict.entrySet()) {
			if (sb == null || count == SPLICE_NAMEDICT_LIMIT) {
				if (sb != null) {
					sb.append("	}\n");
					r.add(sb.toString());
				}
				sb = new StringBuilder("{\n");
				count = 0;
			}
			sb.append("		defines.namedict.put(").append(e.getValue()).append(", \"").append(e.getKey())
					.append("\");\n");
			count++;
		}
		if (sb != null) {
			sb.append("	}\n");
			r.add(sb.toString());
		}
		return r;
	}

	void printImport(PrintStream ps) {
		if (Main.variantSupport)
			ps.println("import limax.defines.VariantDefines;");
	}

	private Pair<List<String>, Map<String, String>> printVaraiantViewManagers(String space) {
		List<String> r = new ArrayList<>();
		Map<String, String> map = new HashMap<>();
		PrintStream ps = null;
		ByteArrayOutputStream baos = null;
		int classid = 0;
		int methodid = 0;
		String classname = "";
		for (String body : Stream.concat(printNameDict().stream(),
				Stream.concat(typeids.getBeanInfos().stream().map(info -> printBeanMethodBody(info)),
						views.stream().map(view -> printViewMethodBody(view))))
				.collect(Collectors.toList())) {
			if (ps == null || baos.size() > Viewgen.VIEW_MANAGER_SPLICE_LENGTH_LIMIT) {
				if (ps != null) {
					ps.println("}");
					ps.println();
					map.put(classname, baos.toString());
					ps.close();
				}
				classname = "VD" + classid++;
				ps = new PrintStream(baos = new ByteArrayOutputStream());
				ps.println("package " + space + ";");
				ps.println();
				printImport(ps);
				ps.println();
				ps.println("final class " + classname + " {");
				methodid = 0;
			}
			ps.println("	static void add" + methodid + "(VariantDefines defines) " + body);
			r.add("		" + classname + ".add" + methodid + "(vds);");
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

	Map<String, String> printMethod(PrintStream ps, String space) {
		if (!Main.variantSupport)
			return null;
		Pair<List<String>, Map<String, String>> pair = printVaraiantViewManagers(space);
		ps.println("	@SuppressWarnings(\"unused\")");
		ps.println("	private static VariantDefines getVariantDefines() {");
		ps.println("		VariantDefines vds = new VariantDefines();");
		ps.println();
		pair.getKey().forEach(s -> ps.println(s));
		ps.println();
		ps.println("		return vds;");
		ps.println("	}");
		ps.println();
		return pair.getValue();
	}

}
