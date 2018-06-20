package limax.zdb;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract class Listenable {
	final static Listenable defaultListenable = new ListenableChanged("");

	abstract Listenable copy();

	abstract void setChanged(LogNotify ln);

	abstract void logNotify(Object key, Object value, RecordState rs, ListenerMap listenerMap);

	static Listenable create(Object obj) {
		if (obj instanceof XBean) {
			ListenableBean lb = new ListenableBean();
			for (Field f : XBeanInfo.getFields((XBean) obj))
				switch (f.getType().getName()) {
				case "limax.zdb.SetX":
					lb.add(f.getName(), new ListenableSet(f.getName()));
					break;
				case "java.util.HashMap":
					lb.add(f.getName(), new ListenableMap(f.getName()));
					break;
				default:
					lb.add(f.getName(), new ListenableChanged(f.getName()));
				}
			return lb;
		} else
			return defaultListenable;
	}

	private static class ListenableBean extends Listenable {
		private final Map<String, Listenable> vars = new HashMap<String, Listenable>();
		private boolean changed = false;

		private ListenableBean() {
		}

		private void add(String name, Listenable l) {
			vars.put(name, l);
		}

		@Override
		public ListenableBean copy() {
			ListenableBean l = new ListenableBean();
			vars.forEach((varname, listenable) -> l.vars.put(varname, listenable.copy()));
			return l;
		}

		@Override
		public void setChanged(LogNotify ln) {
			changed = true;
			vars.get(ln.pop().getVarname()).setChanged(ln);
		}

		@Override
		public void logNotify(Object key, Object value, RecordState rs, ListenerMap listenerMap) {
			for (Listenable la : vars.values())
				la.logNotify(key, value, rs, listenerMap);
			switch (rs) {
			case ADDED:
				listenerMap.notifyChanged("", key, value);
				break;
			case REMOVED:
				listenerMap.notifyRemoved("", key, value);
				break;
			case CHANGED:
				if (changed)
					listenerMap.notifyChanged("", key, value, null);
			default:
			}
			changed = false;
		}
	}

	private static class ListenableChanged extends Listenable {
		private final String varname;
		private boolean changed = false;

		ListenableChanged(String varname) {
			this.varname = varname;
		}

		@Override
		public ListenableChanged copy() {
			return new ListenableChanged(varname);
		}

		@Override
		public void setChanged(LogNotify ln) {
			changed = true;
		}

		@Override
		public void logNotify(Object key, Object value, RecordState rs, ListenerMap listenerMap) {
			switch (rs) {
			case ADDED:
				listenerMap.notifyChanged(varname, key, value);
				break;
			case REMOVED:
				listenerMap.notifyRemoved(varname, key, value);
				break;
			case CHANGED:
				if (changed)
					listenerMap.notifyChanged(varname, key, value, null);
			default:
			}
			changed = false;
		}
	}

	private static class ListenableMap extends Listenable {
		private final String varname;
		private Note note;
		private List<XBean> changed;

		ListenableMap(String varname) {
			this.varname = varname;
		}

		@Override
		public ListenableMap copy() {
			return new ListenableMap(varname);
		}

		@Override
		public void setChanged(LogNotify ln) {
			if (ln.isLast()) {
				if (note == null) {
					note = ln.getNote();
				} else {
					((NoteMap<?, ?>) note).merge(ln.getNote());
				}
			} else {
				if (changed == null)
					changed = new ArrayList<>();
				changed.add(ln.pop().getXBean());
			}
		}

		@Override
		@SuppressWarnings({ "unchecked", "rawtypes" })
		public void logNotify(Object key, Object value, RecordState rs, ListenerMap listenerMap) {
			switch (rs) {
			case ADDED:
				listenerMap.notifyChanged(varname, key, value);
				break;
			case REMOVED:
				listenerMap.notifyRemoved(varname, key, value);
				break;
			case CHANGED:
				if ((note != null || changed != null) && listenerMap.hasListener(varname)) {
					NoteMap nMap = (note != null) ? (NoteMap) note : new NoteMap();
					nMap.setChanged(changed, XBeanInfo.getValue((XBean) value, varname));
					listenerMap.notifyChanged(varname, key, value, nMap);
				}
			default:
			}
			note = null;
			changed = null;
		}
	}

	private static class ListenableSet extends Listenable {
		private final String varname;
		private Note note;

		ListenableSet(String varName) {
			this.varname = varName;
		}

		@Override
		public ListenableSet copy() {
			return new ListenableSet(varname);
		}

		@SuppressWarnings("rawtypes")
		@Override
		public void setChanged(LogNotify ln) {
			if (!ln.isLast())
				return;
			if (note == null) {
				note = ln.getNote();
			} else {
				((NoteSet) note).merge(ln.getNote());
			}
		}

		@Override
		public void logNotify(Object key, Object value, RecordState rs, ListenerMap listenerMap) {
			switch (rs) {
			case ADDED:
				listenerMap.notifyChanged(varname, key, value);
				break;
			case REMOVED:
				listenerMap.notifyRemoved(varname, key, value);
				break;
			case CHANGED:
				if (null != note)
					listenerMap.notifyChanged(varname, key, value, note);
			default:
			}
			note = null;
		}
	}

}
