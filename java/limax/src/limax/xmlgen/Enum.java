package limax.xmlgen;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.w3c.dom.Element;

import limax.util.ElementHelper;

public final class Enum extends Naming {

	public Enum(Protocol protocol, Element self) throws Exception {
		super(protocol, self);
		initialize(self);
	}

	public Enum(Bean bean, Element self) throws Exception {
		super(bean, self);
		initialize(self);
	}

	public Enum(Xbean bean, Element self) throws Exception {
		super(bean, self);
		initialize(self);
	}

	public Enum(Cbean bean, Element self) throws Exception {
		super(bean, self);
		initialize(self);
	}

	public Enum(View view, Element self) throws Exception {
		super(view, self);
		initialize(self);
	}

	private long value;
	private String comment;
	private String strtype;
	private Type valueType = null;

	private static final Set<String> typeset = new HashSet<>(Arrays.asList("byte", "short", "int", "long"));

	private void initialize(Element self) {
		ElementHelper eh = new ElementHelper(self);
		value = eh.getLong("value");
		strtype = eh.getString("type", "int");
		eh.warnUnused("name");
		comment = Variable.extractComment(self);
	}

	@Override
	boolean resolve() {
		if (!typeset.contains(strtype))
			throw new RuntimeException("wrong enum type \"" + strtype + "\"");
		valueType = Type.resolve(this, strtype, null, null);
		if (null == valueType)
			throw new RuntimeException("wrong enum type \"" + strtype + "\"");
		return super.resolve();
	}

	public Type getType() {
		return valueType;
	}

	public long getValue() {
		return value;
	}

	public String getComment() {
		return comment;
	}

}
