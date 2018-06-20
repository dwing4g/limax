package limax.xmlgen.java;

import java.io.PrintStream;
import java.util.Collection;

import limax.xmlgen.Bean;
import limax.xmlgen.Control;
import limax.xmlgen.Variable;

class ControlFormatter {

	private final Control control;
	private final boolean isServer;
	private final String name;
	private final int controlindex;

	public ControlFormatter(Control control, boolean server, int controlindex) {
		this.control = control;
		this.isServer = server;
		this.name = control.getName();
		this.controlindex = controlindex;
	}

	private String getBaseClass() {
		return isServer ? control.getImplementBean().isConstType()
				? (" implements limax.codec.Marshal, Comparable<" + name + ">") : " implements limax.codec.Marshal"
				: " extends limax.endpoint.View.Control";
	}

	private String getClassPrefix() {
		if (isServer)
			return "protected static";
		else
			return "private";
	}

	public void make(PrintStream ps, String prefix) {
		ps.println(prefix + getClassPrefix() + " final class " + name + getBaseClass() + " {");
		if (isServer)
			printServerDefine(ps, prefix);
		else
			printClientDefine(ps, prefix);
		ps.println(prefix + "}");
		ps.println();
		if (!isServer)
			printClientMethod(ps, prefix);

	}

	private static String getParamList(Collection<Variable> variables) {
		StringBuilder params = new StringBuilder();
		variables.forEach(var -> params.append(",  _").append(var.getName()).append("_"));
		return params.delete(0, 3).toString();
	}

	private void printClientMethod(PrintStream ps, String prefix) {
		Bean bean = control.getImplementBean();
		ps.println(prefix + "public void " + bean.getLastName() + "("
				+ ConstructWithParam.getParamList(bean.getVariables()) + ")");
		ps.println(prefix
				+ "		throws InstantiationException, ClassCastException, limax.net.SizePolicyException, limax.codec.CodecException {");
		ps.println(prefix + "	new " + bean.getLastName() + "(" + getParamList(bean.getVariables()) + ").send();");
		ps.println(prefix + "}");
		ps.println();
	}

	private void printClientDefine(PrintStream ps, String prefix) {
		Bean bean = control.getImplementBean();
		Declare.make(bean.getEnums(), bean.getVariables(), Declare.Type.PRIVATE, ps, prefix + "    ");
		ConstructWithParam.make(bean, ps, prefix + "	");
		Marshal.make(bean, ps, prefix + "	");
		Unmarshal.make(bean, ps, prefix + "	");
		ps.println();
		ps.println(prefix + "	@Override");
		ps.println(prefix + "	public byte getControlIndex() {");
		ps.println(prefix + "		return " + controlindex + ";");
		ps.println(prefix + "	}");
	}

	public void printServerDefine(PrintStream ps, String prefix) {
		new BeanFormatter(control.getImplementBean()).printDefine(ps, prefix, false);
	}

}
