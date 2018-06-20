package limax.xmlgen;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.w3c.dom.Element;

import limax.util.StringUtils;
import limax.util.XMLUtils;

public abstract class Naming {

	public static class Root extends Naming {

		private final TreeMap<String[], Naming> symbols = new TreeMap<>(new Comparator<String[]>() {
			@Override
			public int compare(String[] o1, String[] o2) {
				int l1 = o1.length;
				int l2 = o2.length;
				int c;
				while ((c = o1[--l1].compareTo(o2[--l2])) == 0 && l1 > 0 && l2 > 0)
					;
				return c != 0 ? c : l1 - l2;
			}
		});

		private int counter = 0;

		public Root() {
			super();
		}

		public <T extends Naming> T search(String ref[], Class<T> clazz) {
			T r = null;
			for (Map.Entry<String[], Naming> e : symbols.tailMap(ref).entrySet()) {
				String[] key = e.getKey();
				int l1 = ref.length;
				int l2 = key.length;
				int c;
				while ((c = ref[--l1].compareTo(key[--l2])) == 0 && l1 > 0 && l2 > 0)
					;
				if (c != 0 || l1 != 0)
					break;
				Naming n = e.getValue();
				if (n.resolved && clazz.isInstance(n)) {
					if (r != null) {
						dump(System.err);
						throw new RuntimeException("Ambiguous symbol\n" + r + "\r------------------\n\n" + n);
					}
					r = clazz.cast(n);
				}
			}
			return r;
		}

		public Collection<Naming> compile() {
			Set<Naming> unresolved = new LinkedHashSet<>();
			compile(unresolved);
			for (int target = 0; target != unresolved.size();) {
				target = unresolved.size();
				List<Naming> items = new ArrayList<>(unresolved);
				unresolved.clear();
				items.forEach(i -> i.compile(unresolved));
			}
			return unresolved;
		}
	}

	final static Root typeRoot = new Root();

	static {
		typeRoot.resolved();
	}

	private static String prefix = "limax.xmlgen.";

	public static void setPrefix(String s) {
		prefix = s;
	}

	private final String name;
	private final String fullname[];
	private Object att;
	private Naming parent;
	private Root root;
	private Set<Naming> children = new LinkedHashSet<>();
	private boolean resolved;

	private Naming() {
		this.parent = null;
		root = (Root) this;
		this.name = "";
		this.fullname = new String[] { Integer.toHexString(root.counter++), "" };
	}

	public Naming(Naming parent, String name) {
		this.parent = parent;
		this.root = parent.root;
		this.name = name;
		List<String> list = new ArrayList<>();
		for (Naming p = parent; p != null; p = p.parent)
			list.add(0, p.name);
		list.add(0, Integer.toHexString(root.counter++));
		list.add(name);
		this.fullname = list.toArray(new String[0]);
		parent.children.stream().filter(i -> !name.isEmpty() && name.equals(i.name)).findAny().ifPresent(n -> {
			throw new RuntimeException("Duplicate name " + this);
		});
		parent.children.add(this);
		root.symbols.put(this.fullname, this);
	}

	Naming(Naming parent, Element self, String key) throws Exception {
		this(parent, self.getAttribute(key).trim());
		for (Element e : XMLUtils.getChildElements(self))
			Class.forName(prefix + StringUtils.upper1(e.getNodeName())).getConstructor(getClass(), Element.class)
					.newInstance(this, e);
	}

	public Naming(Naming parent, Element self) throws Exception {
		this(parent, self, "name");
	}

	public String getName() {
		return name;
	}

	<T extends Naming> T search(String ref[], Class<T> clazz) {
		return root.search(ref, clazz);
	}

	<T extends Naming> T dereference(String ref[], Class<T> clazz) {
		T v = root.search(ref, clazz);
		if (v != null)
			root.symbols.remove(fullname);
		return v;
	}

	void resolved() {
		resolved = true;
	}

	boolean resolve() {
		for (Naming child : children)
			if (!child.resolved)
				return false;
		return true;
	}

	@Override
	public String toString() {
		if (parent == null)
			return "";
		List<Naming> list = new ArrayList<>();
		for (Naming p = parent; p.parent != null; p = p.parent)
			list.add(0, p);
		list.add(this);
		StringBuilder sb = new StringBuilder();
		String tab = "\t";
		for (Naming n : list) {
			sb.append("[").append(n.getClass().getSimpleName()).append("]").append(n.getName()).append("\n")
					.append(tab);
			tab += "\t";
		}
		return sb.toString();
	}

	final void compile(Set<Naming> symbolList) {
		for (int target = 0; target != children.size();) {
			target = children.size();
			for (Naming child : new ArrayList<Naming>(children))
				child.compile(symbolList);
		}
		if (!resolved)
			if (resolve())
				resolved = true;
			else
				symbolList.add(this);
	}

	public final Set<Naming> getChildren() {
		return children;
	}

	public final <T extends Naming> List<T> getChildren(Class<T> clazz) {
		List<T> list = new ArrayList<>();
		for (Naming child : children)
			if (clazz.isInstance(child))
				list.add(clazz.cast(child));
		return list;
	}

	public final <T extends Naming> T getChild(Class<T> clazz, String name) {
		for (Naming child : children)
			if (clazz.isInstance(child) && child.name.equals(name))
				return clazz.cast(child);
		return null;
	}

	private final <T extends Naming> void getDescendants(List<T> list, Class<T> clazz) {
		for (Naming child : children) {
			if (clazz.isInstance(child))
				list.add(clazz.cast(child));
			child.getDescendants(list, clazz);
		}
	}

	final <T extends Naming> List<T> getDescendants(Class<T> clazz) {
		List<T> list = new ArrayList<>();
		getDescendants(list, clazz);
		return list;
	}

	public final Naming getParent() {
		return parent;
	}

	public final Root getRoot() {
		return root;
	}

	private final void dump(PrintStream ps, String tab) {
		ps.println(tab + "[" + getClass().getSimpleName() + "]" + name + (!resolved ? "[-]" : ""));
		for (Naming child : children)
			child.dump(ps, tab + "\t");
	}

	public final void dump(PrintStream ps) {
		if (parent == null)
			for (Naming child : children)
				child.dump(ps, "");
		else
			dump(ps, "");
	}

	public void attachment(Object o) {
		this.att = o;
	}

	public Object attachment() {
		return this.att;
	}
}
