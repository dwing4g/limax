package limax.xmlgen.cpp;

import java.io.PrintStream;
import java.util.Collection;

import limax.xmlgen.Bean;
import limax.xmlgen.Control;
import limax.xmlgen.Variable;

class ControlFormatter {
	private final Control control;
	private final int controlindex;

	public ControlFormatter(Control control, int controlindex) {
		this.control = control;
		this.controlindex = controlindex;
	}

	public void makeClass(PrintStream ps) {

		ps.println("		class __" + control.getName() + " : public limax::View::Control");
		ps.println("		{");

		printDefines(ps);

		ps.println("		};");
		ps.println();

	}

	public void makeMethod(PrintStream ps) {
		final Bean bean = control.getImplementBean();
		ps.println(
				"		void " + bean.getLastName() + "(" + ParamName.getParamList(bean.getVariables()) + ") const");
		ps.println("		{");
		ps.println("			__" + bean.getLastName() + "(" + getParamList(bean.getVariables()) + ").send(this);");
		ps.println("		}");
		ps.println();
	}

	private static String getParamList(Collection<Variable> variables) {
		StringBuilder params = new StringBuilder();
		variables.forEach(var -> params.append(", _").append(var.getName()).append("_"));
		return params.delete(0, 2).toString();
	}

	private void printDefines(PrintStream ps) {

		final Bean bean = control.getImplementBean();

		ps.println("		public:");

		BeanFormatter.declareVariables(ps, bean.getVariables(), "			");
		BeanFormatter.declareInitConstruct(ps, "__" + control.getName(), bean.getVariables(), "			");
		Marshal.make(bean, ps, "			");
		Unmarshal.make(bean, ps, "			");

		ps.println("		protected:");
		ps.println("			int8_t getControlIndex() const override");
		ps.println("			{");
		ps.println("				return " + controlindex + ";");
		ps.println("			}");
	}
}
