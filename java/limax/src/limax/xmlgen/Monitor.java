package limax.xmlgen;

import org.w3c.dom.Element;

import limax.util.ElementHelper;
import limax.util.StringUtils;

public class Monitor extends Naming {

	public enum Type {
		Counter, Gauge,
	}

	private Type type = Type.Counter;

	public Monitor(Monitorset parent, Element self) throws Exception {
		super(parent, self);
		ElementHelper eh = new ElementHelper(self);
		type = Type.valueOf(StringUtils.upper1(eh.getString("type", "Counter").toLowerCase()));
		eh.warnUnused("name");
	}

	public Type getType() {
		return type;
	}

}
