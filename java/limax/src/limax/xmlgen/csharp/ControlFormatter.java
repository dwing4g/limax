package limax.xmlgen.csharp;

import java.io.PrintStream;
import java.util.Collection;

import limax.xmlgen.Bean;
import limax.xmlgen.Control;
import limax.xmlgen.Variable;

final class ControlFormatter {
	private final Control control;
	private final int viewindex;
	private final int controlindex;

	public ControlFormatter(Control control, int viewindex, int controlindex) {
		this.control = control;
		this.viewindex = viewindex;
		this.controlindex = controlindex;
	}

	public void make(PrintStream ps) {
		ps.println("		private sealed class __" + control.getName() + " : View.Control");
		ps.println("		{");
		printDefines(ps);
		ps.println("		};");
		final Bean bean = control.getImplementBean();
		ps.println("		public void " + control.getName() + "("
				+ ConstructWithParam.getParamList(bean.getVariables()) + ")");
		ps.println("		{");
		String paramList = getParamList(bean.getVariables());
		paramList = paramList.isEmpty() ? "" : ", " + paramList;
		ps.println("			new __" + control.getName() + "(this" + paramList + ").send();");
		ps.println("		}");
	}

	private static String getParamList(Collection<Variable> variables) {
		final StringBuilder params = new StringBuilder();
		for (Variable var : variables)
			params.append(",  _").append(var.getName()).append("_");
		return params.delete(0, 2).toString();
	}

	private void printDefines(PrintStream ps) {
		Define.make(control, ps, "			");
		ConstructWithParam.make(control, ps, "			");
		Marshal.make(control, ps, "			");
		Unmarshal.make(control, ps, "			");
		ps.println("			public override short getViewClassIndex()");
		ps.println("			{");
		ps.println("				return " + viewindex + ";");
		ps.println("			}");
		ps.println("			public override byte getControlIndex()");
		ps.println("			{");
		ps.println("				return " + controlindex + ";");
		ps.println("			}");
	}
}
