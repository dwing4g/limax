package limax.xmlgen.csharp;

import java.io.File;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import limax.provider.ViewLifecycle;
import limax.xmlgen.Cbean;
import limax.xmlgen.FileOperation;
import limax.xmlgen.Main;
import limax.xmlgen.NameStringToIndex;
import limax.xmlgen.Namespace;
import limax.xmlgen.Service;
import limax.xmlgen.Type;
import limax.xmlgen.View;
import limax.xmlgen.Xbean;

public class Viewgen {

	private final File viewGenDir;
	private final File viewSrcDir;
	private final File beanDir;
	private final File xmlgensDir;
	private final Service service;
	private final Collection<View> views;
	private final Namespace namespace;
	private final NameStringToIndex nameindex = new NameStringToIndex(Short.MAX_VALUE);
	private final Map<View, ViewFormatter> formatters = new IdentityHashMap<>();

	public Viewgen(Service service, Namespace namespace) {
		this.service = service;
		this.viewGenDir = new File(Main.outputPath, Xmlgen.path_views);
		this.viewSrcDir = new File(Main.outputPath, Xmlgen.path_xmlsrc);
		this.beanDir = new File(Main.outputPath, Xmlgen.path_beans);
		this.xmlgensDir = new File(Main.outputPath, Xmlgen.path_xmlgen);
		this.views = namespace.getViews();
		this.namespace = namespace;
		nameindex.addAll(this.views);
	}

	Service getService() {
		return service;
	}

	ViewFormatter getViewFormatter(View view) {
		return formatters.get(view);
	}

	String makeViewNameSpace(View view) {
		return service.getFullName() + "." + view.getFirstName();
	}

	String getProviderNamespace() {
		return service.getFullName() + "." + namespace.getFullName();
	}

	public void make() {
		Set<Type> dependTypes = new HashSet<Type>();
		for (final View view : views)
			view.depends(dependTypes);

		for (final Type type : dependTypes) {
			if (type instanceof Xbean)
				new XbeanFormatter((Xbean) type).make(beanDir);
			else if (type instanceof Cbean)
				new CbeanFormatter((Cbean) type).make(beanDir);
		}

		for (View view : views)
			formatters.put(view, new ViewFormatter(this, view, nameindex.getIndex(view)));
		for (View view : views)
			if (view.getLifecycle() != ViewLifecycle.temporary)
				formatters.get(view).make(viewGenDir, viewSrcDir);
		for (View view : views)
			if (view.getLifecycle() == ViewLifecycle.temporary)
				formatters.get(view).make(viewGenDir, viewSrcDir);
		if (!views.isEmpty())
			makeManager();
	}

	private void makeManager() {
		String space = getProviderNamespace();
		try (PrintStream ps = FileOperation.fopen(xmlgensDir,
				BeanFormatter.getOutputFileName(space) + ".viewmanager.cs")) {
			BeanFormatter.printCommonInclude(ps);
			ps.println("using limax.endpoint;");
			ps.println("namespace " + space);
			ps.println("{");
			ps.println("	public sealed class ViewManager : View.StaticManager");
			ps.println("	{");
			ps.println("		private readonly int pvid;");
			ps.println("		private readonly IDictionary<short, Type> classes;");

			ps.println("		private ViewManager(int pvid) {");
			ps.println("			this.pvid = pvid;");
			ps.println("			IDictionary<short, Type> map = new Dictionary<short,Type>();");
			for (final View view : views) {
				ps.println("			map.Add(" + nameindex.getIndex(view) + ", typeof(" + service.getFullName() + "."
						+ view.getFullName() + "));");
			}
			ps.println("			classes = map;");
			ps.println("		}");
			ps.println("		public static ViewManager createInstance(int pvid)");
			ps.println("		{");
			ps.println("			return new ViewManager(pvid);");
			ps.println("		}");
			ps.println("		public int getProviderId()");
			ps.println("		{");
			ps.println("			return pvid;");
			ps.println("		}");
			ps.println("		public IDictionary<short, Type> getClasses()");
			ps.println("		{");
			ps.println("			return classes;");
			ps.println("		}");
			ps.println("	}");
			ps.println("}");
		}
	}
}
