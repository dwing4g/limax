package limax.xmlgen;

import java.util.List;
import java.util.Set;

import org.w3c.dom.Element;

import limax.util.ElementHelper;

public final class Cbean extends Type {
	private String comment;
	private boolean json = false;
	private boolean builder = false;
	private boolean touch = false;

	public Cbean(Project parent, Element self) throws Exception {
		super(parent, self);
		initialize(self);
	}

	public Cbean(Namespace parent, Element self) throws Exception {
		super(parent, self);
		initialize(self);
	}

	public Cbean(Zdb parent, Element self) throws Exception {
		super(parent, self);
		initialize(self);
	}

	private void initialize(Element self) {
		Variable.verifyName(this);
		comment = Bean.extractComment(self);
		ElementHelper eh = new ElementHelper(self);
		json = eh.getBoolean("json", false);
		builder = eh.getBoolean("builder", false);
		eh.warnUnused("name", "xml:base", "xmlns:xi");
	}

	public Cbean(Zdb parent, String name) {
		super(parent, name);
	}

	@Override
	boolean resolve() {
		if (!super.resolve())
			return false;
		if (!isConstType())
			throw new RuntimeException(getFullName() + " is not constant bean. only immutable variable is available");
		if (json && !isJSONSerializable())
			throw new RuntimeException("Cbean " + getFullName() + " is not JSONSerializable.");
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

	public boolean isBuilderEnabled() {
		return builder;
	}

	@Override
	public boolean isJSONSerializable() {
		return json && !getVariables().stream()
				.filter(var -> var.isJSONEnabled() && !var.getType().isJSONSerializable()).findFirst().isPresent();
	}

	public List<Variable> getVariables() {
		return getChildren(Variable.class);
	}

	public Variable getVariable(String varname) {
		return getChild(Variable.class, varname);
	}

	public List<Enum> getEnums() {
		return getChildren(Enum.class);
	}

	public String getLastName() {
		return getName();
	}

	public String getFirstName() {
		return "cbean";
	}

	public String getFullName() {
		return getFirstName() + "." + getName();
	}

	public boolean touch() {
		boolean tmp = touch;
		touch = true;
		return tmp;
	}
}
