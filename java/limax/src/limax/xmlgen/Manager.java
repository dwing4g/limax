package limax.xmlgen;

import java.util.HashSet;
import java.util.Set;

import org.w3c.dom.Element;

import limax.util.ElementHelper;

public final class Manager extends Naming {
	private State init;
	private String initstate;
	private String port;
	private String type;
	private long sessionTimeout = 0;

	public Manager(Service service, Element self) throws Exception {
		super(service, self);
		initialize(self);
	}

	void initialize(Element self) {
		ElementHelper eh = new ElementHelper(self);
		initstate = eh.getString("initstate");
		port = eh.getString("port").trim();
		type = eh.getString("type").toLowerCase();
		if (isProvider()) {
			sessionTimeout = eh.getLong("sessionTimeout", 0);
			if (sessionTimeout < 0)
				sessionTimeout = 0;
		}
		eh.warnUnused("name");
	}

	@Override
	public boolean resolve() {
		if (!super.resolve())
			return false;
		init = search(initstate.split("\\."), State.class);
		if (Main.isCpp) {
			if (getStates().size() != 1)
				throw new RuntimeException("Support single namepsace in state define in cxx.");
		}
		return init != null;
	}

	public Set<State> getStates() {
		Set<State> set = new HashSet<>(getChildren(State.class));
		set.add(init);
		return set;
	}

	public Namespace bindProviderNamespace() {
		if (init.getNamespaces().size() != 1 || init.getChildren().size() != 1)
			throw new RuntimeException("only one namespace can contains in provider");
		return init.getNamespaces().get(0);
	}

	public String getInitStateName() {
		return initstate;
	}

	public boolean isServer() {
		return type.equalsIgnoreCase("server");
	}

	public boolean isProvider() {
		return type.equalsIgnoreCase("provider");
	}

	public boolean isClient() {
		return type.equalsIgnoreCase("client");
	}

	public String getPort() {
		return port;
	}

	public long getSessionTimeout() {
		return sessionTimeout;
	}

}
