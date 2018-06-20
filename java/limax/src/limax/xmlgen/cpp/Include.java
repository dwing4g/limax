package limax.xmlgen.cpp;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import limax.xmlgen.Bean;
import limax.xmlgen.Bind;
import limax.xmlgen.Cbean;
import limax.xmlgen.Subscribe;
import limax.xmlgen.Type;
import limax.xmlgen.TypeAny;
import limax.xmlgen.TypeBinary;
import limax.xmlgen.TypeBoolean;
import limax.xmlgen.TypeByte;
import limax.xmlgen.TypeDouble;
import limax.xmlgen.TypeFloat;
import limax.xmlgen.TypeInt;
import limax.xmlgen.TypeList;
import limax.xmlgen.TypeLong;
import limax.xmlgen.TypeMap;
import limax.xmlgen.TypeSet;
import limax.xmlgen.TypeShort;
import limax.xmlgen.TypeString;
import limax.xmlgen.TypeVector;
import limax.xmlgen.Variable;
import limax.xmlgen.View;
import limax.xmlgen.Visitor;
import limax.xmlgen.Xbean;

class Include implements Visitor {
	private final Set<String> includes = new HashSet<>();
	private final String incPerfix;

	private static String[] includes(Collection<Type> deps, String incPerfix, Collection<String> incs) {
		final Include vistor = new Include(incPerfix);
		for (final Type type : deps)
			type.accept(vistor);

		for (String s : incs) {
			if (!s.isEmpty())
				vistor.includes.add(s);
		}

		final String[] sortedIncludes = vistor.includes.toArray(new String[0]);
		java.util.Arrays.sort(sortedIncludes);
		return sortedIncludes;
	}

	public static String[] includes(Type bean, String incPerfix, String... incs) {
		final Set<Type> deps = new HashSet<Type>();
		bean.depends(deps);
		deps.remove(bean);
		return includes(deps, incPerfix, Arrays.asList(incs));
	}

	public static String[] includes(View view, String incPerfix, String... incs) {
		final Set<Type> deps = new HashSet<Type>();
		for (final Variable var : view.getVariables())
			var.getType().depends(deps);
		for (final Bind bind : view.getBinds()) {
			if (bind.isFullBind())
				bind.getValueType().depends(deps);
		}

		final Set<String> addincs = new HashSet<>();
		addincs.addAll(Arrays.asList(incs));
		for (final Subscribe ref : view.getSubscribes()) {
			if (ref.getVariable() != null) {
				ref.getVariable().getType().depends(deps);
			} else {
				final Bind bind = ref.getBind();
				if (bind.isFullBind())
					bind.getValueType().depends(deps);
				else
					addincs.add("#include \"" + ref.getView().getFullName() + ".h\"");
			}
		}
		return includes(deps, incPerfix, addincs);
	}

	private Include(String incPerfix) {
		this.incPerfix = incPerfix;
	}

	@Override
	public void visit(Bean bean) {
		final String inc = "#include \"" + incPerfix + BeanFormatter.getOutputFileName(bean.getFullName()) + ".h\"";
		this.includes.add(inc);
	}

	@Override
	public void visit(TypeByte type) {
	}

	@Override
	public void visit(TypeFloat type) {
	}

	@Override
	public void visit(TypeDouble type) {
	}

	@Override
	public void visit(TypeInt type) {
	}

	@Override
	public void visit(TypeShort type) {

	}

	@Override
	public void visit(TypeList type) {
		this.includes.add("#include <list>");
	}

	@Override
	public void visit(TypeLong type) {
	}

	@Override
	public void visit(TypeMap type) {
		this.includes.add("#include <unordered_map>");
	}

	@Override
	public void visit(TypeBinary type) {
	}

	@Override
	public void visit(TypeSet type) {
		this.includes.add("#include <unordered_set>");
	}

	@Override
	public void visit(TypeString type) {
	}

	@Override
	public void visit(TypeVector type) {
		this.includes.add("#include <vector>");
	}

	@Override
	public void visit(Cbean type) {
		this.includes.add("#include \"" + incPerfix + type.getFullName() + ".h\"");
	}

	@Override
	public void visit(Xbean type) {
		this.includes.add("#include \"" + incPerfix + type.getFullName() + ".h\"");
	}

	@Override
	public void visit(TypeBoolean type) {
	}

	@Override
	public void visit(TypeAny type) {
		throw new UnsupportedOperationException();
	}

}
