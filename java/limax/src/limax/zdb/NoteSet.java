package limax.zdb;

import java.util.HashSet;
import java.util.Set;

public class NoteSet<E> implements Note {
	private final Set<E> added = new HashSet<E>();
	private final Set<E> removed = new HashSet<E>();
	private final Set<E> eldest = new HashSet<E>();

	NoteSet() {
	}

	final void merge(Note note) {
		@SuppressWarnings("unchecked")
		NoteSet<E> another = (NoteSet<E>) note;
		for (E e : another.added)
			logAdd(e);
		for (E e : another.removed)
			logRemove(e);
	}

	final void logAdd(E e) {
		if (!removed.remove(e))
			added.add(e);
	}

	final void logRemove(E e) {
		if (added.remove(e))
			return;
		removed.add(e);
		if (!eldest.contains(e))
			eldest.add(e);
	}

	public final Set<E> getAdded() {
		return added;
	}

	public final Set<E> getRemoved() {
		return removed;
	}

	final Set<E> getEldest() {
		return eldest;
	}

	final boolean isSetChanged() {
		return !added.isEmpty() || !removed.isEmpty();
	}

	final void clear() {
		added.clear();
		removed.clear();
		eldest.clear();
	}

	@Override
	public String toString() {
		return "added=" + added + " removed=" + removed;
	}
}
