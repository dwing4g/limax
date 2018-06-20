package limax.xmlgen.cpp;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import limax.provider.ViewLifecycle;
import limax.xmlgen.Cbean;
import limax.xmlgen.Main;
import limax.xmlgen.NameStringToIndex;
import limax.xmlgen.Namespace;
import limax.xmlgen.Service;
import limax.xmlgen.Type;
import limax.xmlgen.View;
import limax.xmlgen.Xbean;

public class Viewgen {

	private final File viewDir;
	private final File beanDir;
	private final Service service;
	private final Collection<View> views;
	private final NameStringToIndex viewindex = new NameStringToIndex(Short.MAX_VALUE);
	private final Map<View, ViewFormatter> formatters = new IdentityHashMap<>();

	public Viewgen(Service service, Namespace namespace) {
		this.service = service;
		this.viewDir = new File(Main.outputPath, Xmlgen.path_views);
		this.beanDir = new File(Main.outputPath, Xmlgen.path_beans);
		this.views = namespace.getViews();
		this.viewindex.addAll(this.views);
	}

	Service getService() {
		return service;
	}

	ViewFormatter getViewFormatter(View view) {
		return formatters.get(view);
	}

	public void make() {
		Set<Type> dependTypes = new HashSet<Type>();
		for (final View view : views)
			view.depends(dependTypes);
		for (final Type type : dependTypes) {
			if (type instanceof Xbean) {
				new XbeanFormatter((Xbean) type).make(beanDir);
				Xmlgen.allbeanhpps.add(((Xbean) type).getFullName() + ".h");
			} else if (type instanceof Cbean) {
				new CbeanFormatter((Cbean) type).make(beanDir);
				Xmlgen.allbeanhpps.add(((Cbean) type).getFullName() + ".h");
			}
		}
		for (View view : views)
			formatters.put(view, new ViewFormatter(this, view, viewindex.getIndex(view)));
		for (View view : views) {
			if (view.getLifecycle() != ViewLifecycle.temporary)
				formatters.get(view).make(viewDir, new File(Main.outputPath, Xmlgen.path_xmlgen_src));
		}
		for (View view : views) {
			if (view.getLifecycle() == ViewLifecycle.temporary)
				formatters.get(view).make(viewDir, new File(Main.outputPath, Xmlgen.path_xmlgen_src));
		}
	}

}
