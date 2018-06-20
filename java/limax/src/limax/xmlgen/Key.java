package limax.xmlgen;

import org.w3c.dom.Element;

public class Key extends Variable {

	public Key(Monitorset parent, Element self) throws Exception {
		super(parent, self);
		Variable.verifyName(this);
	}

	@Override
	public boolean resolve() {
		if (!super.resolve())
			return false;
		if (!getType().isConstType())
			throw new RuntimeException("Counterset Key " + ((Monitorset) getParent()).getFullName() + "." + getName()
					+ " is not primitive const type.");
		if (getType() instanceof Cbean)
			throw new RuntimeException(
					"Counterset Key " + ((Monitorset) getParent()).getFullName() + "." + getName() + " is CBean type.");
		return true;
	}
}
