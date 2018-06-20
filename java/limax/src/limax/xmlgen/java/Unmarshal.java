package limax.xmlgen.java;

import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import limax.xmlgen.Bean;
import limax.xmlgen.Bind;
import limax.xmlgen.Cbean;
import limax.xmlgen.Main;
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
import limax.xmlgen.Xbean.DynamicVariable;

public class Unmarshal implements Visitor {
	private final String varname;
	private final PrintStream ps;
	private final String prefix;

	public static void make(View view, PrintStream ps, String prefix) {
		ps.println(prefix + "@Override");
		ps.println(prefix
				+ "public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {");
		view.getVariables().forEach(var -> var.getType().accept(new Unmarshal(var.getName(), ps, prefix + "	")));
		view.getBinds().forEach(bind -> {
			if (bind.isFullBind())
				bind.getValueType().accept(new Unmarshal(bind.getName(), ps, prefix + "	"));
			else
				ps.println(prefix + "	_os_.unmarshal(" + bind.getName() + ");");
		});
		ps.println(prefix + "	return _os_;");
		ps.println(prefix + "}");
		ps.println();
	}

	public static void make(Collection<Variable> variables, PrintStream ps, String prefix) {
		ps.println(prefix + "@Override");
		ps.println(prefix
				+ "public limax.codec.OctetsStream unmarshal(limax.codec.OctetsStream _os_) throws limax.codec.MarshalException {");
		variables.forEach(var -> make(var, ps, prefix + "	"));
		ps.println(prefix + "	return _os_;");
		ps.println(prefix + "}");
		ps.println();
	}

	public static Collection<Runnable> make(Collection<Variable> svars, Collection<DynamicVariable> dvars, String outer,
			boolean isStandardXBean, PrintStream ps, String prefix) {
		if (isStandardXBean) {
			svars.forEach(var -> {
				Define.beginXbeanParent_VarName(var.getName());
				make(var, ps, prefix + "	");
				Define.endXbeanParent_VarName();
			});
		} else {
			svars.forEach(var -> make(var, ps, prefix + "	"));
		}
		if (dvars != null) {
			if (!dvars.isEmpty()) {
				ps.println(prefix
						+ "	limax.zdb.XBean.DynamicData _dynamicData_ = new limax.zdb.XBean.DynamicData(_os_);");
				ps.println(prefix + "	if (_dynamicData_.size() > 0) {");
				ps.println(prefix + "		_Dynamic_ _dynamic_ = new _Dynamic_(_dynamicData_);");
				dvars.stream().filter(DynamicVariable::isMaster).forEach(var -> {
					String name = var.getName() + var.getSerial();
					if (isStandardXBean && var.getType() instanceof TypeBinary) {
						ps.println(prefix + "		limax.codec.Octets " + name + " = _dynamic_." + name + "();");
						ps.println(prefix + "		if (" + name + " != null)");
						ps.println(prefix + "			this." + var.getName() + " = " + name + ".getBytes();");
					} else {
						ps.println(prefix + "		" + TypeName.getBoxingName(var.getType()) + " " + name
								+ " = _dynamic_." + name + "();");
						ps.println(prefix + "		if (" + name + " != null)");
						ps.println(prefix + "			this." + var.getName() + " = ("
								+ TypeName.getName(var.getType()) + ")" + name + ";");
					}
				});
				ps.println(prefix + "	}");
			} else
				ps.println(prefix + "	new limax.zdb.XBean.DynamicData(_os_);");
		}
		ps.println(prefix + "	return _os_;");
		return dvars != null && !dvars.isEmpty() ? Collections.singleton(() -> {
			ps.println(prefix + "private class _Dynamic_ {");
			ps.println(prefix + "	private final limax.zdb.XBean.DynamicData _items_;");
			ps.println(prefix + "	private final java.util.Map<Integer, Object> _datas_ = new java.util.HashMap<>();");
			ps.println();
			ps.println(prefix + "	public _Dynamic_(limax.zdb.XBean.DynamicData _items_) {");
			ps.println(prefix + "		this._items_ = _items_;");
			ps.println(prefix + "	}");
			ps.println();
			dvars.forEach(var -> {
				String type = var.getType() instanceof TypeBinary ? "limax.codec.Octets"
						: TypeName.getBoxingName(var.getType());
				String name = var.getName();
				int serial = var.getSerial();
				if (type.endsWith(">"))
					ps.println(prefix + "	@SuppressWarnings(\"unchecked\")");
				ps.println(prefix + "	" + (var.isMaster() ? "public" : "private") + " " + type + " " + name + serial
						+ "() throws MarshalException {");
				ps.println(prefix + "		if (_datas_.containsKey(" + serial + "))");
				ps.println(prefix + "			return (" + type + ")_datas_.get(" + serial + ");");
				ps.println(prefix + "		limax.codec.Octets o = _items_.get(" + serial + ");");
				ps.println(prefix + "		if (o != null) {");
				ps.println(prefix + "			OctetsStream _os_ = OctetsStream.wrap(o);");
				if (isStandardXBean)
					Define.beginXbeanParent_VarName(name, outer + ".this");
				ConstructWithUnmarshal.make(var.getType(), name, ps, prefix + "			");
				if (isStandardXBean)
					Define.endXbeanParent_VarName();
				ps.println(prefix + "			_datas_.put(" + serial + ", " + name + ");");
				ps.println(prefix + "			return " + name + ";");
				ps.println(prefix + "		}");
				List<String> scripts = var.scripts();
				if (scripts.size() > 1 || !scripts.get(0).isEmpty()) {
					List<Object> depends = var.depends();
					depends.stream().filter(obj -> obj instanceof Integer).map(obj -> (Integer) obj)
							.map(s -> dvars.stream().filter(v0 -> v0.getSerial() == s).findAny().get()).forEach(v -> {
								String vtype = v.getType() instanceof TypeBinary ? "limax.codec.Octets"
										: TypeName.getBoxingName(v.getType());
								String vname = v.getName() + v.getSerial();
								if (vtype.endsWith(">"))
									ps.println(prefix + "   		@SuppressWarnings(\"unchecked\")");
								ps.println(prefix + "   		" + vtype + " " + vname + " = (" + vtype + ")" + vname
										+ "();");
								ps.println(prefix + "   		if (" + vname + " == null) {");
								ps.println(prefix + "			_datas_.put(" + serial + ", null);");
								ps.println(prefix + "   			return null;");
								ps.println(prefix + "   		}");
							});
					StringBuilder sb = new StringBuilder();
					int i = 0;
					for (; i < depends.size(); i++) {
						sb.append(scripts.get(i));
						Object v = depends.get(i);
						if (v instanceof String) {
							sb.append(outer + ".this." + v);
						} else {
							int s = (Integer) v;
							sb.append(dvars.stream().filter(v0 -> v0.getSerial() == s).findAny().get().getName() + s);
						}
					}
					sb.append(scripts.get(i));
					ps.println(prefix + "  		" + type + " " + name + " = " + sb.toString() + ";");
					ps.println(prefix + "  		_datas_.put(" + serial + ", " + name + ");");
					ps.println(prefix + "		return " + name + ";");
				} else {
					ps.println(prefix + "		_datas_.put(" + serial + ", null);");
					ps.println(prefix + "		return null;");
				}
				ps.println(prefix + "	}");
				ps.println();
			});
			ps.println(prefix + "}");
			ps.println();
		}) : Collections.emptyList();
	}

	public static void make(Bean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Cbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Xbean bean, PrintStream ps, String prefix) {
		make(bean.getVariables(), ps, prefix);
	}

	public static void make(Bind bind, PrintStream ps, String prefix) {
		make(bind.getVariables(), ps, prefix);
	}

	public static void make(Variable var, PrintStream ps, String prefix) {
		var.getType().accept(new Unmarshal("this." + var.getName(), ps, prefix));
	}

	public static void make(Type type, String varname, PrintStream ps, String prefix) {
		type.accept(new Unmarshal(varname, ps, prefix));
	}

	private Unmarshal(String varname, PrintStream ps, String prefix) {
		this.varname = varname;
		this.ps = ps;
		this.prefix = prefix;
	}

	@Override
	public void visit(TypeByte type) {
		ps.println(prefix + varname + " = _os_.unmarshal_byte();");
	}

	@Override
	public void visit(TypeFloat type) {
		ps.println(prefix + varname + " = _os_.unmarshal_float();");
	}

	@Override
	public void visit(TypeDouble type) {
		ps.println(prefix + varname + " = _os_.unmarshal_double();");
	}

	@Override
	public void visit(TypeInt type) {
		ps.println(prefix + varname + " = _os_.unmarshal_int();");
	}

	@Override
	public void visit(TypeShort type) {
		ps.println(prefix + varname + " = _os_.unmarshal_short();");
	}

	@Override
	public void visit(TypeLong type) {
		ps.println(prefix + varname + " = _os_.unmarshal_long();");
	}

	@Override
	public void visit(TypeBinary type) {
		if (Main.isMakingZdb)
			ps.println(prefix + varname + " = _os_.unmarshal_bytes();");
		else
			ps.println(prefix + varname + " = _os_.unmarshal_Octets();");
	}

	@Override
	public void visit(TypeString type) {
		ps.println(prefix + varname + " = _os_.unmarshal_String();");
	}

	private void unmarshalContainer(Type valueType) {
		ps.println(prefix + "for(int _i_ = _os_.unmarshal_size(); _i_ > 0; --_i_) {");
		ConstructWithUnmarshal.make(valueType, "_v_", ps, prefix + "	");
		ps.println(prefix + "	" + varname + ".add(_v_);");
		ps.println(prefix + "}");
	}

	@Override
	public void visit(TypeList type) {
		this.unmarshalContainer(type.getValueType());
	}

	@Override
	public void visit(TypeVector type) {
		this.unmarshalContainer(type.getValueType());
	}

	@Override
	public void visit(TypeSet type) {
		this.unmarshalContainer(type.getValueType());
	}

	private void unmarshalContainer(Type keytype, Type valuetype) {
		ps.println(prefix + "for(int _i_ = _os_.unmarshal_size(); _i_ > 0; --_i_) {");
		ConstructWithUnmarshal.make(keytype, "_k_", ps, prefix + "	");
		ConstructWithUnmarshal.make(valuetype, "_v_", ps, prefix + "	");
		ps.println(prefix + "	" + varname + ".put(_k_, _v_);");
		ps.println(prefix + "}");
	}

	@Override
	public void visit(TypeMap type) {
		unmarshalContainer(type.getKeyType(), type.getValueType());
	}

	@Override
	public void visit(Bean bean) {
		ps.println(prefix + varname + ".unmarshal(_os_);");
	}

	@Override
	public void visit(Cbean type) {
		ps.println(prefix + varname + ".unmarshal(_os_);");
	}

	@Override
	public void visit(Xbean type) {
		ps.println(prefix + varname + ".unmarshal(_os_);");
	}

	@Override
	public void visit(TypeBoolean type) {
		ps.println(prefix + varname + " = _os_.unmarshal_boolean();");
	}

	@Override
	public void visit(TypeAny type) {
		throw new UnsupportedOperationException();
	}
}
