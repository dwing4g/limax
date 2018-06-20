package limax.xmlgen;

import java.util.List;

import org.w3c.dom.Element;

import limax.util.ElementHelper;

public class Control extends Naming {
	private Bean implementBean;

	public Control(View parent, Element self) throws Exception {
		super(parent, self);
		Variable.verifyName(this);
		implementBean = new Bean(this, self);
		new ElementHelper(self).warnUnused("name");
	}

	public List<Variable> getVairables() {
		return getChildren(Variable.class);
	}

	public View getView() {
		return (View) getParent();
	}

	public Bean getImplementBean() {
		return implementBean;
	}

	public String getFullName() {
		return getView().getName() + "." + getName();
	}
}
