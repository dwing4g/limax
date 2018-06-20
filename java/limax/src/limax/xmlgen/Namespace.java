package limax.xmlgen;

import java.util.List;

import org.w3c.dom.Element;

import limax.util.ElementHelper;

public final class Namespace extends Naming {
	private Namespace self;
	private int pvid;

	public Namespace(Project project, Element self) throws Exception {
		super(project, self);
		if (getName().contains("."))
			throw new RuntimeException("Namespace name " + getName() + " contains '.'");
		if (getName().equalsIgnoreCase("states"))
			throw new RuntimeException("Outer most namespace name \"states\" is not permitted.");
		if (getName().equalsIgnoreCase("solver"))
			throw new RuntimeException("Outer most namespace name \"solver\" is not permitted.");
		this.self = this;
		ElementHelper eh = new ElementHelper(self);
		pvid = eh.getInt("pvid", pvid);
		if (pvid < 0 || pvid > 0xffffff)
			throw new RuntimeException(getFullName() + ": pvid = " + pvid + " not in range[0, 0xFFFFFF]");
		eh.warnUnused("name", "xml:base", "xmlns:xi");
	}

	public Namespace(Namespace parent, Element self) throws Exception {
		super(parent, self);
		if (getName().contains("."))
			throw new RuntimeException("Namespace name " + getName() + " contains '.'");
		this.self = this;
		new ElementHelper(self).warnUnused("name", "xml:base", "xmlns:xi");
	}

	public Namespace(State state, Element self) throws Exception {
		super(state, self, "ref");
		new ElementHelper(self).warnUnused("ref");
	}

	@Override
	public boolean resolve() {
		if (!super.resolve())
			return false;
		if (self == null) {
			self = dereference(getName().split("\\."), getClass());
			return self != null;
		}
		return true;
	}

	public String getPvidName() {
		if (self.getParent() instanceof Namespace)
			return ((Namespace) self.getParent()).getPvidName();
		return self.getFullName().replace('.', '_').toUpperCase() + "_PVID";
	}

	public int getPvid() {
		if (self.getParent() instanceof Namespace)
			return ((Namespace) self.getParent()).getPvid();
		return self.pvid;
	}

	public List<Protocol> getProtocols() {
		return self.getDescendants(Protocol.class);
	}

	public List<Rpc> getRpcs() {
		return self.getDescendants(Rpc.class);
	}

	public List<View> getViews() {
		return self.getDescendants(View.class);
	}

	public List<Monitorset> getCounterset() {
		return self.getDescendants(Monitorset.class);
	}

	public String getFullName() {
		StringBuilder sb = new StringBuilder("." + self.getName());
		for (Naming p = self.getParent(); p instanceof Namespace; p = p.getParent())
			sb.insert(0, p.getName()).insert(0, ".");
		return sb.deleteCharAt(0).toString();
	}

	public String getAbsoluteFullName() {
		StringBuilder sb = new StringBuilder("." + self.getName());
		for (Naming p = self.getParent(); p != getRoot(); p = p.getParent())
			sb.insert(0, p.getName()).insert(0, ".");
		return sb.deleteCharAt(0).toString();
	}

	public String getLastName() {
		return self.getName();
	}

}
