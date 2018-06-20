package limax.xmlgen;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.w3c.dom.Element;

import limax.provider.ViewLifecycle;
import limax.util.ElementHelper;

public class View extends Naming implements Dependency {
	private ViewLifecycle lifecycle;
	private long tick;
	private NameStringToIndex varnameindex;
	private String comment = "";

	public View(Namespace parent, Element self) throws Exception {
		super(parent, self);
		ElementHelper eh = new ElementHelper(self);
		lifecycle = ViewLifecycle.valueOf(eh.getString("lifecycle").toLowerCase());
		tick = eh.getLong("tick", 10);
		eh.warnUnused("name");
		comment = Bean.extractComment(self);
		if (!(parent.getParent() instanceof Namespace) && getName().equalsIgnoreCase("Marshals"))
			throw new RuntimeException(
					"View name \"" + getName() + "\" is not permitted in root namespace \"" + parent.getName() + "\".");
		if (!(parent.getParent() instanceof Namespace) && getName().equalsIgnoreCase("ViewManager"))
			throw new RuntimeException(
					"View name \"" + getName() + "\" is not permitted in root namespace \"" + parent.getName() + "\".");
	}

	@Override
	public boolean resolve() {
		if (!super.resolve())
			return false;
		Namespace namespace = (Namespace) getParent();
		if (namespace.getPvid() == 0)
			throw new RuntimeException("view must contain in namespace with pvid != 0");
		getVariables().forEach(var -> {
			if (var.getType().isAny())
				throw new RuntimeException(
						"View variable " + getFullName() + "." + var.getName() + " depend XBean with type any.");
			if (var.getType() instanceof Bean && ((Bean) var.getType()).getVariables().isEmpty())
				throw new RuntimeException("View variable " + getFullName() + "." + var.getName()
						+ " depend empty Bean = " + var.getTypeString());
		});
		varnameindex = new NameStringToIndex(Byte.MAX_VALUE);
		varnameindex.addAll(getVariables());
		varnameindex.addAll(getBinds());
		varnameindex.addAll(getSubscribes());
		varnameindex.addAll(getControls());
		return true;
	}

	public int getPvid() {
		return ((Namespace) getParent()).getPvid();
	}

	public List<Enum> getEnums() {
		return getChildren(Enum.class);
	}

	public List<Variable> getVariables() {
		return getChildren(Variable.class);
	}

	public ViewLifecycle getLifecycle() {
		return lifecycle;
	}

	public String getComment() {
		return comment;
	}

	public List<Bind> getBinds() {
		return getChildren(Bind.class);
	}

	public List<Subscribe> getSubscribes() {
		return getChildren(Subscribe.class);
	}

	public List<Control> getControls() {
		return getChildren(Control.class);
	}

	@Override
	public void depends(Set<Type> types) {
		Stream.concat(getVariables().stream(), getBinds().stream()).forEach(i -> i.depends(types));
	}

	public String getLastName() {
		return getName();
	}

	public long getTick() {
		return tick;
	}

	public String getFirstName() {
		StringBuilder sb = new StringBuilder();
		for (Naming p = getParent(); p instanceof Namespace; p = p.getParent())
			sb.insert(0, p.getName()).insert(0, ".");
		return sb.deleteCharAt(0).toString();
	}

	public String getFullName() {
		return getFirstName() + "." + getName();
	}

	public boolean isAutoSyncToClient() {
		return ViewLifecycle.session == lifecycle || ViewLifecycle.temporary == lifecycle;
	}

	public boolean isManualSyncToClient() {
		return ViewLifecycle.global == lifecycle;
	}

	public NameStringToIndex getMemberNameStringToIndex() {
		return varnameindex;
	}
}
