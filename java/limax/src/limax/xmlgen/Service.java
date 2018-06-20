package limax.xmlgen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.w3c.dom.Element;

import limax.util.ElementHelper;

public final class Service extends Naming {

	private boolean useGlobalId = false;
	private boolean useZdb = false;

	public Service(Project project, Element self) throws Exception {
		super(project, self);
		initialize(self);
	}

	private void initialize(Element self) throws Exception {
		ElementHelper eh = new ElementHelper(self);
		useGlobalId = eh.getBoolean("useGlobalId", false);
		useZdb = eh.getBoolean("useZdb", false);
		eh.warnUnused("name");
	}

	public boolean isUseGlobalId() {
		return useGlobalId;
	}

	public boolean isUseZdb() {
		return useZdb;
	}

	public Set<Protocol> getProtocols() {
		Set<Protocol> protocols = new HashSet<Protocol>();
		for (Manager m : getManagers())
			for (State s : m.getStates())
				protocols.addAll(s.getProtocols());
		return protocols;
	}

	public List<Rpc> getRpcs() {
		List<Rpc> rpcs = new ArrayList<Rpc>();
		for (Manager m : getManagers())
			for (State s : m.getStates())
				rpcs.addAll(s.getRpcs());
		return rpcs;
	}

	public Set<Namespace> getNamespaces() {
		Set<Namespace> set = new HashSet<>();
		for (Manager m : getManagers())
			for (State s : m.getStates())
				set.addAll(s.getNamespaces());
		return set;
	}

	public List<Manager> getManagers() {
		return getChildren(Manager.class);
	}

	public boolean hasServerOrProviderManager() {
		for (Manager m : getManagers())
			if (m.isServer() || m.isProvider())
				return true;
		return false;
	}

	public String getFullName() {
		StringBuilder sb = new StringBuilder("." + getName());
		for (Naming p = getParent(); p != getRoot(); p = p.getParent())
			sb.insert(0, p.getName()).insert(0, ".");
		return sb.deleteCharAt(0).toString();
	}

	public void make() throws Exception {
		Main.isMakingView = true;
		if (Main.isCpp) {
			if (getManagers().size() != 1)
				throw new RuntimeException("Support single manager in cxx.");
			new limax.xmlgen.cpp.Netgen(this).make();
			for (final Namespace n : getNamespaces())
				new limax.xmlgen.cpp.Viewgen(this, n).make();
			new limax.xmlgen.cpp.Xmlgen(this).make();
		} else if (Main.isCSharp) {
			if (getManagers().size() != 1)
				throw new RuntimeException("Support single manager in csharp.");
			new limax.xmlgen.csharp.Netgen(this).make();
			for (final Namespace n : getNamespaces())
				new limax.xmlgen.csharp.Viewgen(this, n).make();
		} else {
			for (Manager manager : getManagers()) {
				if (manager.isProvider()) {
					for (State s : manager.getStates())
						for (Namespace n : s.getNamespaces())
							new limax.xmlgen.java.Viewgen(this, n).makeJavaServer();
				} else if (manager.isClient()) {
					for (State s : manager.getStates())
						for (Namespace n : s.getNamespaces())
							new limax.xmlgen.java.Viewgen(this, n).makeJavaClient();
				}
			}
			new limax.xmlgen.java.Netgen(this).make();
		}
		Main.isMakingView = false;
	}
}
