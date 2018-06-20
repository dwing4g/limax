package limax.xmlgen;

import org.w3c.dom.Element;

import limax.provider.ViewLifecycle;
import limax.util.ElementHelper;

public final class Subscribe extends Naming {
	private String subs;
	private View view;
	private Variable var;
	private Bind bind;
	private boolean snapshot;

	public Subscribe(View view, Element self) throws Exception {
		super(view, self);
		Variable.verifyName(this);
		ElementHelper eh = new ElementHelper(self);
		subs = eh.getString("ref");
		snapshot = eh.getBoolean("snapshot", false);
		eh.warnUnused("name", "ref");
	}

	@Override
	public boolean resolve() {
		if (!super.resolve())
			return false;
		if (((View) getParent()).getLifecycle() != ViewLifecycle.temporary)
			return false;
		String path[] = subs.split("\\.");
		Naming parent;
		if ((var = dereference(path, Variable.class)) != null)
			parent = var.getParent();
		else if ((bind = dereference(path, Bind.class)) != null)
			parent = bind.getParent();
		else
			return false;
		if (!(parent instanceof View))
			return false;
		view = (View) parent;
		return view.getLifecycle() == ViewLifecycle.session;
	}

	public Variable getVariable() {
		return var;
	}

	public Bind getBind() {
		return bind;
	}

	public boolean isSnapshot() {
		return snapshot;
	}

	public View getView() {
		return view;
	}
}
