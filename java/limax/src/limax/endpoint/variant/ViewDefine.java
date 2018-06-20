package limax.endpoint.variant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import limax.defines.VariantBeanDefine;
import limax.defines.VariantDefines;
import limax.defines.VariantNameIds;
import limax.defines.VariantVariableDefine;
import limax.defines.VariantViewControlDefine;
import limax.defines.VariantViewDefine;
import limax.defines.VariantViewVariableDefine;

final class ViewDefine {
	final String viewName;
	final short classindex;
	final boolean isTemporary;
	final Collection<VariableDefine> vars = new ArrayList<VariableDefine>();
	final Collection<ControlDefine> ctrls = new ArrayList<ControlDefine>();

	final static class BindVarDefine {
		final String name;
		final MarshalMethod method;

		public BindVarDefine(String name, Declaration decl) {
			this.name = name;
			this.method = decl.createMarshalMethod();
		}
	}

	final static class VariableDefine {
		final byte varindex;
		final boolean isSubscribe;
		final boolean isBind;
		final String name;
		final MarshalMethod method;

		final Map<Byte, BindVarDefine> bindVars = new HashMap<Byte, BindVarDefine>();

		public VariableDefine(byte varindex, boolean isSubscribe, boolean isBind, String name, Declaration decl) {
			this.varindex = varindex;
			this.isSubscribe = isSubscribe;
			this.isBind = isBind;
			this.name = name;
			this.method = decl.createMarshalMethod();
		}
	}

	final static class ControlDefine {
		final byte ctrlindex;
		final String name;
		final MarshalMethod method;

		public ControlDefine(byte ctrlindex, String name, Declaration decl) {
			this.ctrlindex = ctrlindex;
			this.name = name;
			this.method = decl.createMarshalMethod();
		}
	}

	@Override
	public String toString() {
		return viewName;
	}

	ViewDefine(short classindex, String viewName, boolean isTemporary) {
		this.viewName = viewName;
		this.classindex = classindex;
		this.isTemporary = isTemporary;
	}

	static class VariantDefineParser {

		private static class DeclarationStore {

			private final Map<Integer, Declaration> basemap = new HashMap<Integer, Declaration>();
			private final Map<Integer, Declaration> beanmap = new HashMap<Integer, Declaration>();

			DeclarationStore() {
				basemap.put(VariantDefines.BASE_TYPE_BINARY, VariantType.Binary.createDeclaration());
				basemap.put(VariantDefines.BASE_TYPE_BOOLEAN, VariantType.Boolean.createDeclaration());
				basemap.put(VariantDefines.BASE_TYPE_BYTE, VariantType.Byte.createDeclaration());
				basemap.put(VariantDefines.BASE_TYPE_DOUBLE, VariantType.Double.createDeclaration());
				basemap.put(VariantDefines.BASE_TYPE_FLOAT, VariantType.Float.createDeclaration());
				basemap.put(VariantDefines.BASE_TYPE_INT, VariantType.Int.createDeclaration());
				basemap.put(VariantDefines.BASE_TYPE_LONG, VariantType.Long.createDeclaration());
				basemap.put(VariantDefines.BASE_TYPE_SHORT, VariantType.Short.createDeclaration());
				basemap.put(VariantDefines.BASE_TYPE_STRING, VariantType.String.createDeclaration());
			}

			public void add(int type, Declaration dec) {
				beanmap.put(type, dec);
			}

			private Declaration getBase(int type, int typeKey, int typeValue) {
				switch (type) {
				case VariantDefines.BASE_TYPE_LIST:
					return VariantType.List.createDeclaration(get(typeValue));
				case VariantDefines.BASE_TYPE_MAP:
					return VariantType.Map.createDeclaration(get(typeKey), get(typeValue));
				case VariantDefines.BASE_TYPE_SET:
					return VariantType.Set.createDeclaration(get(typeValue));
				case VariantDefines.BASE_TYPE_VECTOR:
					return VariantType.Vector.createDeclaration(get(typeValue));
				default:
					return basemap.get(type);
				}
			}

			private Declaration get(int type) {
				return type < VariantDefines.BASE_TYPE_MAX ? basemap.get(type) : beanmap.get(type);
			}

			public Declaration get(int type, int typeKey, int typeValue) {
				return type < VariantDefines.BASE_TYPE_MAX ? getBase(type, typeKey, typeValue) : beanmap.get(type);
			}
		}

		private final VariantDefines defines;

		private final Collection<ViewDefine> views = new ArrayList<ViewDefine>();
		private final DeclarationStore declarationStore = new DeclarationStore();

		VariantDefineParser(VariantDefines defines) {
			this.defines = defines;
		}

		private String getName(VariantNameIds ids) {
			final StringBuilder sb = new StringBuilder();
			boolean had = false;
			for (int i : ids.ids) {
				if (had)
					sb.append('.');
				sb.append(defines.namedict.get(i));
				had = true;
			}
			return sb.toString();
		}

		private Declaration parseStructDeclaration(Collection<VariantVariableDefine> vars) {
			StructDeclarationCreator decl = new StructDeclarationCreator();
			for (VariantVariableDefine vvd : vars)
				decl.addFieldDefinition(defines.namedict.get(vvd.name),
						declarationStore.get(vvd.type, vvd.typeKey, vvd.typeValue));
			return decl.create();
		}

		private VariantBeanDefine findVariantBeanDefine(int type) {
			if (declarationStore.basemap.containsKey(type))
				return null;
			for (VariantBeanDefine bean : defines.beans) {
				if (bean.type == type)
					return bean;
			}
			throw new RuntimeException("lost bean type = " + type);
		}

		private void parseBeans() {
			for (VariantBeanDefine vbd : defines.beans)
				declarationStore.add(vbd.type, parseStructDeclaration(vbd.vars));
		}

		private void makeBindVariables(int type, VariableDefine define) {
			final VariantBeanDefine beandefine = findVariantBeanDefine(type);
			if (null != beandefine) {
				byte fieldindex = 0;
				for (VariantVariableDefine var : beandefine.vars)
					define.bindVars.put(fieldindex++, new BindVarDefine(defines.namedict.get(var.name),
							declarationStore.get(var.type, var.typeKey, var.typeValue)));
			}
		}

		private ViewDefine parseView(VariantViewDefine viewdef) {
			final ViewDefine viewDefine = new ViewDefine(viewdef.clsindex, getName(viewdef.name), viewdef.istemp);
			byte index = 0;
			for (VariantViewVariableDefine vardef : viewdef.vars) {
				final VariableDefine define = new VariableDefine(index++, false, vardef.bind,
						defines.namedict.get(vardef.name),
						declarationStore.get(vardef.type, vardef.typeKey, vardef.typeValue));
				if (vardef.bind)
					makeBindVariables(vardef.type, define);
				viewDefine.vars.add(define);
			}
			for (VariantViewVariableDefine subdef : viewdef.subs) {
				final VariableDefine define = new VariableDefine(index++, true, subdef.bind,
						defines.namedict.get(subdef.name),
						declarationStore.get(subdef.type, subdef.typeKey, subdef.typeValue));
				if (subdef.bind)
					makeBindVariables(subdef.type, define);
				viewDefine.vars.add(define);
			}
			for (VariantViewControlDefine control : viewdef.ctrls)
				viewDefine.ctrls.add(new ControlDefine(index++, defines.namedict.get(control.name),
						parseStructDeclaration(control.vars)));
			return viewDefine;
		}

		private void parseViews() {
			for (VariantViewDefine viewdef : defines.views)
				views.add(parseView(viewdef));
		}

		void parse(Map<Short, Object> viewdefines, Set<String> tempviewnames) {
			parseBeans();
			parseViews();
			for (ViewDefine vd : views) {
				viewdefines.put(vd.classindex, vd);
				if (vd.isTemporary)
					tempviewnames.add(vd.viewName);
			}
		}
	}
}
