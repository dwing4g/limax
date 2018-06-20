package limax.xmlgen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import org.w3c.dom.Element;

import limax.util.ElementHelper;

public class Xbean extends Type {
	private final Map<Integer, DynamicVariable> dynamicVariables = new LinkedHashMap<>();
	private String comment;
	private boolean json = false;
	private int nextserial;

	public static final class DynamicVariable {
		private final String name;
		private final int serial;
		private Type type;
		private List<String> scripts = new ArrayList<>();
		private List<Object> scriptDepends = new ArrayList<>();
		private boolean master;

		DynamicVariable(String name, int serial, Type type) {
			this.name = name;
			this.serial = serial;
			this.type = type;
		}

		public DynamicVariable(String name, int serial) {
			this.name = name;
			this.serial = serial;
		}

		public String getName() {
			return name;
		}

		public int getSerial() {
			return serial;
		}

		public Type getType() {
			return type;
		}

		public List<String> scripts() {
			return scripts;
		}

		public List<Object> depends() {
			return scriptDepends;
		}

		public boolean isMaster() {
			return master;
		}

		public DynamicVariable script(String script) {
			scripts.add(script);
			return this;
		}

		public DynamicVariable depend(Object depend) {
			scriptDepends.add(depend);
			return this;
		}

		public DynamicVariable setMaster() {
			master = true;
			return this;
		}
	}

	public Xbean(Zdb parent, Element self) throws Exception {
		super(parent, self);
		Variable.verifyName(this);
		comment = Bean.extractComment(self);
		ElementHelper eh = new ElementHelper(self);
		json = eh.getBoolean("json", false);
		nextserial = eh.getInt("nextserial", 0);
		eh.warnUnused("name", "xml:base", "xmlns:xi");
	}

	public Xbean(Zdb parent, String name) {
		super(parent, name);
		this.nextserial = Integer.MAX_VALUE;
	}

	@Override
	boolean resolve() {
		if (!super.resolve())
			return false;
		if (json && !isJSONSerializable())
			throw new RuntimeException(getFullName() + " is not JSONSerializable.");
		Queue<Integer> running = new ArrayDeque<>();
		Set<Integer> done = new HashSet<>();
		getVariables().stream().filter(Variable::isDynamic).peek(var -> running.add(var.getSerial()))
				.forEach(var -> var.expandDynamicVariable(dynamicVariables));
		while (!running.isEmpty()) {
			int serial = running.poll();
			done.add(serial);
			dynamicVariables.get(serial).depends().stream().filter(obj -> obj instanceof Integer)
					.map(obj -> (Integer) obj).filter(s -> !done.contains(s)).forEach(s -> running.add(s));
		}
		Set<Integer> all = new HashSet<>(dynamicVariables.keySet());
		all.removeAll(done);
		if (!all.isEmpty()) {
			System.err.println("WARN: " + getFullName() + "'s dynamicVariable has unused serial = " + all);
			dynamicVariables.keySet().removeAll(all);
		}
		return true;
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visit(this);
	}

	@Override
	public void depends(Set<Type> incls) {
		if (incls.add(this))
			getVariables().forEach(var -> var.getType().depends(incls));
	}

	public String getComment() {
		return comment;
	}

	public boolean isJSONEnabled() {
		return json;
	}

	@Override
	public boolean isJSONSerializable() {
		return json && !getVariables().stream()
				.filter(var -> var.isJSONEnabled() && !var.getType().isJSONSerializable()).findFirst().isPresent();
	}

	public List<Variable> getVariables() {
		return getChildren(Variable.class);
	}

	public List<Variable> getStaticVariables() {
		return getVariables().stream().filter(var -> !var.isDynamic()).collect(Collectors.toList());
	}

	public Collection<Xbean.DynamicVariable> getDynamicVariables() {
		return dynamicVariables.values();
	}

	public boolean isDynamic() {
		return ((Zdb) getParent()).isDynamic();
	}

	public Variable getVariable(String varname) {
		return getVariables().stream().filter(var -> var.getName().equals(varname)).findAny().orElse(null);
	}

	public List<Enum> getEnums() {
		return getChildren(Enum.class);
	}

	public String getLastName() {
		return getName();
	}

	public String getFirstName() {
		return "xbean";
	}

	public String getFullName() {
		return getFirstName() + "." + getName();
	}

	@Override
	public boolean isConstType() {
		return false;
	}

	boolean appendDynamicVariable(String name, int serial, Type type) {
		return dynamicVariables.put(serial, new DynamicVariable(name, serial, type)) == null;
	}

	Type setMasterDynamicVariable(int serial) {
		if (nextserial <= serial)
			throw new RuntimeException(getFullName() + "'s attribute nextserial=\"" + nextserial + "\" is too small.");
		return dynamicVariables.get(serial).setMaster().getType();
	}

	public void replaceDynamicVariablesFrom(Xbean xbean) {
		dynamicVariables.clear();
		dynamicVariables.putAll(xbean.dynamicVariables);
	}
}
