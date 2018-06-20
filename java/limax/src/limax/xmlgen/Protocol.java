package limax.xmlgen;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.w3c.dom.Element;

import limax.util.ElementHelper;

public class Protocol extends Naming {
	private Protocol self;
	private int type;
	private int maxsize;
	private Bean implementBean;

	public Protocol(Namespace namespace, Element self) throws Exception {
		super(namespace, self);
		initialize(self);
	}

	public Protocol(State state, Element self) throws Exception {
		super(state, self, "ref");
		new ElementHelper(self).warnUnused("ref");
	}

	private void initialize(Element self) throws Exception {
		Variable.verifyName(this);
		this.self = this;
		implementBean = new Bean(this, self);
		ElementHelper eh = new ElementHelper(self);
		this.type = eh.getInt("type");
		this.maxsize = eh.getInt("maxsize");
		if (this.type < 0 || this.type > 255)
			throw new RuntimeException(getFullName() + ": type = " + this.type + " not in range[0,255]");
		if (this.maxsize < 0)
			throw new RuntimeException(getFullName() + ": maxsize = " + maxsize + " < 0");
		eh.warnUnused("name", "json");
	}

	@Override
	public boolean resolve() {
		if (!super.resolve())
			return false;
		if (self == null) {
			self = dereference(super.getName().split("\\."), getClass());
			return self != null;
		}
		Main.checkReserveType(getFullName(), type);
		return true;
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

	public Set<Bean> dependBeans() {
		Set<Type> types = new HashSet<>();
		self.implementBean.depends(types);
		types.remove(self.implementBean);
		Set<Bean> depends = new TreeSet<>(new Comparator<Bean>() {
			@Override
			public int compare(Bean o1, Bean o2) {
				return o1.getFullName().compareTo(o2.getFullName());
			}
		});
		for (Type type : types)
			if (type instanceof Bean)
				depends.add((Bean) type);
		return depends;
	}
}
