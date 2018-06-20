package limax.xmlgen;

import org.w3c.dom.Element;

import limax.util.ElementHelper;

public final class Rpc extends Naming {
	private Rpc self;

	private String argument;
	private String result;
	private int type;
	private int maxsize;
	private int timeout;

	private Bean implementBean;
	private Bean argumentBean;
	private Bean resultBean;

	public Rpc(Namespace namespace, Element self) throws Exception {
		super(namespace, self);
		initialize(self);
	}

	public Rpc(State state, Element self) throws Exception {
		super(state, self, "ref");
		new ElementHelper(self).warnUnused("ref");
	}

	void initialize(Element self) throws Exception {
		Variable.verifyName(this);
		this.self = this;
		implementBean = new Bean(this, self);
		ElementHelper eh = new ElementHelper(self);
		timeout = eh.getInt("timeout");
		argument = eh.getString("argument");
		result = eh.getString("result");
		type = eh.getInt("type");
		maxsize = eh.getInt("maxsize");
		if (this.type <= 0 || this.type > 255)
			throw new RuntimeException(getFullName() + ": type = " + this.type + " not in range[0,255]");
		if (maxsize < 0)
			throw new RuntimeException(getFullName() + ": maxsize = " + maxsize + " < 0");
		eh.warnUnused("name");
	}

	@Override
	public boolean resolve() {
		if (!super.resolve())
			return false;
		if (self == null) {
			self = dereference(super.getName().split("\\."), getClass());
			return self != null;
		}
		if ((argumentBean = search(argument.split("\\."), Bean.class)) == null)
			return false;
		if ((resultBean = search(result.split("\\."), Bean.class)) == null)
			return false;
		Main.checkReserveType(getFullName(), type);
		return true;
	}

	public Bean getArgumentBean() {
		return self.argumentBean;
	}

	public Bean getResultBean() {
		return self.resultBean;
	}

	public int getTimeout() {
		return self.timeout;
	}

	public int getType() {
		return self.type;
	}

	public int getMaxsize() {
		return self.maxsize;
	}

	public Bean getImplementBean() {
		return self.implementBean;
	}

	public String getLastName() {
		return self.getName();
	}

	public String getFirstName() {
		StringBuilder sb = new StringBuilder();
		for (Naming p = self.getParent(); p instanceof Namespace; p = p.getParent())
			sb.insert(0, p.getName()).insert(0, ".");
		return sb.deleteCharAt(0).toString();
	}

	public String getFullName() {
		return getFirstName() + "." + getLastName();
	}
}
