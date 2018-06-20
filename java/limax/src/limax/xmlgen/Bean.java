package limax.xmlgen;

import java.util.List;
import java.util.Set;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import limax.util.ElementHelper;

public final class Bean extends Type {
	private String comment;
	private boolean json = false;

	public Bean(Namespace namespace, Element self) throws Exception {
		super(namespace, self);
		initialize(self);
	}

	Bean(Protocol parent, Element self) throws Exception {
		super(parent, self);
		initialize(self);
	}

	Bean(Rpc parent, Element self) throws Exception {
		super(parent, self);
		initialize(self);
	}

	Bean(Control parent, Element self) throws Exception {
		super(parent, self);
		initialize(self);
	}

	public void initialize(Element self) {
		Variable.verifyName(this);
		comment = extractComment(self);
		ElementHelper eh = new ElementHelper(self);
		json = eh.getBoolean("json", false);
	}

	static String extractComment(Element self) {
		for (Node c = self.getPreviousSibling(); null != c; c = c.getPreviousSibling()) {
			if (Node.ELEMENT_NODE == c.getNodeType())
				break;
			if (Node.COMMENT_NODE == c.getNodeType())
				return "/** " + c.getTextContent().trim() + " **/";
			if (Node.CDATA_SECTION_NODE == c.getNodeType())
				return "/**" + c.getTextContent() + "*/";
		}
		return "";
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

	@Override
	boolean resolve() {
		if (!super.resolve())
			return false;
		getVariables().stream().filter(var -> var.getType().isAny()).findFirst().ifPresent(var -> {
			Naming parent = getParent();
			final String who;
			if (parent instanceof Protocol)
				who = "Protocol variable " + ((Protocol) parent).getFullName();
			else if (parent instanceof Control)
				who = "Control variable " + ((Control) parent).getView().getFullName() + "."
						+ ((Control) parent).getName();
			else
				who = "Bean variable " + getFullName();
			throw new RuntimeException(who + "." + var.getName() + " depend XBean with type any.");
		});
		if (json && !isJSONSerializable()) {
			Naming parent = getParent();
			final String who;
			if (parent instanceof Protocol)
				who = "Protocol " + ((Protocol) parent).getFullName();
			else
				who = "Bean " + getFullName();
			throw new RuntimeException(who + " is not JSONSerializable.");
		}
		return true;
	}

	public List<Variable> getVariables() {
		return getChildren(Variable.class);
	}

	public List<Enum> getEnums() {
		return getChildren(Enum.class);
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

	public String getLastName() {
		return getName();
	}

	public String getFirstName() {
		StringBuilder sb = new StringBuilder();
		for (Naming p = getParent(); p != getRoot(); p = p.getParent())
			sb.insert(0, p.getName()).insert(0, ".");
		return sb.deleteCharAt(0).toString();
	}

	public String getFullName() {
		return getFirstName() + "." + getName();
	}

}
