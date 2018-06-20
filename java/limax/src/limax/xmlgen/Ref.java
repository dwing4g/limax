package limax.xmlgen;

import org.w3c.dom.Element;

import limax.util.ElementHelper;

public class Ref extends Naming {
	private Variable variable;

	public Ref(Bind parent, Element self) throws Exception {
		super(parent, self);
		new ElementHelper(self).warnUnused("name");
	}

	@Override
	public boolean resolve() {
		if (!super.resolve())
			return false;
		Table table = ((Bind) getParent()).getTable();
		if (table == null)
			return false;
		Type type = table.getValueType();
		if (type == null)
			return false;

		if (type instanceof Xbean) {
			variable = ((Xbean) type).getVariable(getName());
			return variable != null;
		} else if (type instanceof Cbean) {
			variable = ((Cbean) type).getVariable(getName());
			return variable != null;
		}
		return false;
	}

	public Variable getVariable() {
		return variable;
	}
}