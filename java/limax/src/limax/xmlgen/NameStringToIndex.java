package limax.xmlgen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class NameStringToIndex {
	private final int maxSize;

	private final List<Naming> names = new ArrayList<>();

	public NameStringToIndex(int max) {
		maxSize = max;
	}

	static private boolean compareNaming(Naming a, Naming b) {
		return a.getName().equals(b.getName()) && a.getParent().equals(b.getParent());
	}

	public void add(Naming name) {
		if (names.size() > maxSize)
			throw new RuntimeException("too many names");

		for (Naming n : names) {
			if (compareNaming(n, name))
				throw new RuntimeException("duplicate name \"" + name + "\"");
		}
		names.add(name);
	}

	public void addAll(Collection<? extends Naming> names) {
		for (Naming n : names)
			add(n);
	}

	public int getIndex(Naming name) {
		final int count = names.size();
		for (int i = 0; i < count; i++) {
			final Naming n = names.get(i);
			if (compareNaming(n, name))
				return i;
		}
		throw new RuntimeException("lost name \"" + name + "\"");
	}
}
