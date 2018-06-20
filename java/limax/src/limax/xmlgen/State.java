package limax.xmlgen;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import limax.util.ElementHelper;

public final class State extends Naming {
	private State self;

	public State(Project project, Element self) throws Exception {
		super(project, self);
		this.self = this;
		new ElementHelper(self).warnUnused("name");
	}

	public State(Manager manager, Element self) throws Exception {
		super(manager, self, "ref");
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
		getNamespaces().forEach(ns -> {
			if (ns.getParent() instanceof Namespace)
				throw new RuntimeException(
						"in state " + getName() + " inner namespace " + ns.getName() + " is referenced.");
		});
		return true;
	}

	public List<Protocol> getProtocols() {
		List<Protocol> protocols = new ArrayList<Protocol>(self.getChildren(Protocol.class));
		for (Namespace n : self.getChildren(Namespace.class))
			protocols.addAll(n.getProtocols());
		return protocols;
	}

	public List<Rpc> getRpcs() {
		List<Rpc> rpcs = new ArrayList<Rpc>(self.getChildren(Rpc.class));
		for (Namespace n : self.getChildren(Namespace.class))
			rpcs.addAll(n.getRpcs());
		return rpcs;
	}

	public List<Namespace> getNamespaces() {
		return self.getChildren(Namespace.class);
	}

}
