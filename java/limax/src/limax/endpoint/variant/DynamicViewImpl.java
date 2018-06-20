package limax.endpoint.variant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import limax.codec.MarshalException;
import limax.codec.Octets;
import limax.codec.OctetsStream;
import limax.endpoint.ViewChangedType;
import limax.endpoint.ViewVisitor;

final class DynamicViewImpl {
	final int providerId;
	final String viewName;
	final short classindex;

	private final Map<Byte, ViewDefine.VariableDefine> vardefMap = new HashMap<Byte, ViewDefine.VariableDefine>();
	private final Map<String, ViewDefine.ControlDefine> ctrldefMap = new HashMap<String, ViewDefine.ControlDefine>();
	private final Map<String, Variant> variableMap = new HashMap<String, Variant>();
	private final Map<String, Variant> subscribeMap = new HashMap<String, Variant>();
	private final Set<String> fieldnames;
	private ViewChangedType type = ViewChangedType.TOUCH;

	DynamicViewImpl(int providerId, ViewDefine viewDefine) {
		this.providerId = providerId;
		this.viewName = viewDefine.viewName;
		this.classindex = viewDefine.classindex;
		Set<String> set = new HashSet<String>();
		for (ViewDefine.VariableDefine vd : viewDefine.vars) {
			vardefMap.put(vd.varindex, vd);
			set.add(vd.name);
		}
		for (ViewDefine.ControlDefine cd : viewDefine.ctrls)
			ctrldefMap.put(cd.name, cd);
		this.fieldnames = Collections.unmodifiableSet(set);
	}

	final Set<String> getFieldNames() {
		return fieldnames;
	}

	void visitField(String fieldname, ViewVisitor<Variant> visitor) {
		if (!fieldnames.contains(fieldname))
			throw new RuntimeException("In View " + viewName + " field " + fieldname + " not exists.");
		Variant o = variableMap.get(fieldname);
		if (o == null) {
			o = subscribeMap.get(fieldname);
			if (null == o)
				o = Variant.createMap();
		}
		visitor.accept(o);
	}

	void removeSession(long sessionid) {
		for (Variant subs : subscribeMap.values())
			subs.getMapValue().remove(Variant.create(sessionid));
	}

	ViewDefine.ControlDefine getControlDefine(String name) {
		final ViewDefine.ControlDefine def = ctrldefMap.get(name);
		if (null == def)
			throw new IllegalArgumentException("unknown control name \"" + name + "\"");
		return def;
	}

	interface FireViewChanged {
		void onViewChanged(String fieldname, Variant value, ViewChangedType type);
	}

	private Variant removeMemberValue(long sessionid, String name) {
		Variant map = subscribeMap.get(name);
		if (null == map)
			return Variant.Null;
		Variant old = map.getMapValue().remove(Variant.create(sessionid));
		return old == null ? Variant.Null : old;
	}

	private Variant putMemberValue(long sessionid, String name, Variant var) {
		Variant map = subscribeMap.get(name);
		if (null == map)
			subscribeMap.put(name, map = Variant.createMap());
		return map.mapInsert(sessionid, var);
	}

	private Variant getMemberValue(long sessionid, String name) {
		Variant map = subscribeMap.get(name);
		if (null == map)
			return Variant.Null;
		Variant val = map.getMapValue().get(Variant.create(sessionid));
		return val == null ? Variant.Null : val;
	}

	final void onData(long sessionid, byte index, byte field, Octets data, Octets dataremoved, FireViewChanged fvc)
			throws MarshalException {
		ViewDefine.VariableDefine vd = vardefMap.get((byte) (index & 0x7f));
		if (null == vd)
			throw new RuntimeException("view \"" + this + "\" lost var index = \"" + index + "\"");
		if (index < 0) {
			Variant value = vd.isSubscribe ? removeMemberValue(sessionid, vd.name) : variableMap.remove(vd.name);
			fvc.onViewChanged(vd.name, value, ViewChangedType.DELETE);
		} else if (data.size() == 0) {
			Variant value = vd.isSubscribe ? getMemberValue(sessionid, vd.name) : variableMap.get(vd.name);
			fvc.onViewChanged(vd.name, value, type);
			type = ViewChangedType.TOUCH;
		} else if (field < 0) {
			Variant value = vd.method.unmarshal(new OctetsStream(data));
			Variant o = vd.isSubscribe ? putMemberValue(sessionid, vd.name, value) : variableMap.put(vd.name, value);
			fvc.onViewChanged(vd.name, value, o == null || o.isNull() ? ViewChangedType.NEW : ViewChangedType.REPLACE);
		} else {
			Variant value;
			if (vd.isSubscribe) {
				value = getMemberValue(sessionid, vd.name);
				if (value.isNull()) {
					type = ViewChangedType.NEW;
					value = Variant.createStruct();
					putMemberValue(sessionid, vd.name, value);
				} else {
					type = ViewChangedType.REPLACE;
				}
			} else {
				value = variableMap.get(vd.name);
				if (null == value) {
					type = ViewChangedType.NEW;
					value = Variant.createStruct();
					variableMap.put(vd.name, value);
				} else {
					type = ViewChangedType.REPLACE;
				}
			}
			final ViewDefine.BindVarDefine bindvar = vd.bindVars.get(field);
			switch (bindvar.method.getDeclaration().getType()) {
			case Map: {
				Variant n = bindvar.method.unmarshal(OctetsStream.wrap(data));
				Variant v = value.getVariant(bindvar.name);
				if (v.isNull()) {
					v = n;
					value.setValue(bindvar.name, v);
				} else {
					v.getMapValue().putAll(n.getMapValue());
				}
				v.getMapValue().keySet()
						.removeAll(VariantType.Vector
								.createDeclaration(((MapDeclaration) bindvar.method.getDeclaration()).getKey())
								.createMarshalMethod().unmarshal(OctetsStream.wrap(dataremoved)).getCollectionValue());
				break;
			}
			case Set: {
				Variant v = value.getVariant(bindvar.name);
				if (Variant.Null == v) {
					value.setValue(bindvar.name, v = bindvar.method.unmarshal(OctetsStream.wrap(data)));
				} else {
					v.getCollectionValue()
							.addAll(VariantType.Vector
									.createDeclaration(
											((CollectionDeclaration) bindvar.method.getDeclaration()).getValue())
									.createMarshalMethod().unmarshal(OctetsStream.wrap(data)).getCollectionValue());
				}
				v.getCollectionValue()
						.removeAll(VariantType.Vector
								.createDeclaration(((CollectionDeclaration) bindvar.method.getDeclaration()).getValue())
								.createMarshalMethod().unmarshal(OctetsStream.wrap(dataremoved)).getCollectionValue());
				break;
			}
			default:
				value.setValue(bindvar.name, bindvar.method.unmarshal(OctetsStream.wrap(data)));
			}
		}
	}

	Collection<String> getDefineVariableNames() {
		final ArrayList<String> names = new ArrayList<String>();
		for (ViewDefine.VariableDefine vd : vardefMap.values())
			if (!vd.isSubscribe)
				names.add(vd.name);
		return names;
	}

	Collection<String> getDefineSubscribeNames() {
		final ArrayList<String> names = new ArrayList<String>();
		for (ViewDefine.VariableDefine vd : vardefMap.values())
			if (vd.isSubscribe)
				names.add(vd.name);
		return names;
	}

	Collection<String> getDefineControlNames() {
		return Collections.unmodifiableCollection(ctrldefMap.keySet());
	}

}
