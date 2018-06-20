package limax.xmlgen;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.w3c.dom.Element;

import limax.util.ElementHelper;
import limax.xmlgen.java.Zdbgen;

public final class Project extends Naming {

	public Project(Root root, Element self) throws Exception {
		super(root, self);
		new ElementHelper(self).warnUnused("name", "xmlns:xi", "xml:base");
	}

	@Override
	boolean resolve() {
		if (!super.resolve())
			return false;
		if (getChildren(Zdb.class).size() > 1)
			throw new RuntimeException("At most one zdb element in project is permitted.");
		Set<String> serviceNames = getServices().stream().map(i -> i.getName().toLowerCase())
				.collect(Collectors.toSet());
		getChildren(Namespace.class).stream().map(i -> i.getName()).filter(i -> serviceNames.contains(i)).findAny()
				.ifPresent(i -> {
					throw new RuntimeException(
							"Outmost namespace's name can not equalsIgnoreCase with any service's name to avoid potential conflict, here namespace = "
									+ i);
				});
		String dupcbean = getDescendants(Cbean.class).stream().map(Cbean::getName)
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
				.filter(e -> e.getValue() > 1).map(e -> e.getKey() + " occurs " + e.getValue() + " times")
				.collect(Collectors.joining(","));
		if (!dupcbean.isEmpty())
			throw new RuntimeException("Duplicate cbean definition found: " + dupcbean);
		return true;
	}

	public Zdb getZdb() {
		List<Zdb> list = getChildren(Zdb.class);
		return list.isEmpty() ? null : list.get(0);
	}

	public List<Service> getServices() {
		return getChildren(Service.class);
	}

	public boolean isClient() {
		return !getServices().stream().filter(Service::hasServerOrProviderManager).findAny().isPresent();
	}

	boolean hasServerOrProviderOrNoservice() {
		return getServices().stream().filter(Service::hasServerOrProviderManager).findAny().isPresent() ? true
				: getServices().isEmpty();
	}

	public void make() throws Exception {
		List<Bean> beans = getDescendants(Bean.class).stream()
				.filter(b -> b.getParent() instanceof Project || b.getParent() instanceof Namespace)
				.collect(Collectors.toList());
		List<Cbean> cbeans = getDescendants(Cbean.class).stream().collect(Collectors.toList());
		if (Main.isCpp) {
			Main.isMakingNet = true;
			limax.xmlgen.cpp.Netgen.make(beans);
			List<Service> services = getServices();
			if (Main.singleService == null) {
				if (services.size() > 1)
					throw new RuntimeException("Support single service in cxx.");
				services.get(0).make();
			} else {
				boolean found = false;
				for (Service service : services)
					if (service.getName().equals(Main.singleService)) {
						service.make();
						found = true;
						break;
					}
				if (!found)
					throw new RuntimeException("service [" + Main.singleService + "] not found");
			}
			Main.isMakingNet = false;
		} else if (Main.isCSharp) {
			Main.isMakingNet = true;
			limax.xmlgen.csharp.Netgen.make(beans);
			List<Service> services = getServices();
			if (Main.singleService == null) {
				if (services.size() > 1)
					throw new RuntimeException("Support single service in c#.");
				services.get(0).make();
			} else {
				boolean found = false;
				for (Service service : services)
					if (service.getName().equals(Main.singleService)) {
						service.make();
						found = true;
						break;
					}
				if (!found)
					throw new RuntimeException("service [" + Main.singleService + "] not found");
			}
			Main.isMakingNet = false;
		} else {
			Main.isMakingNet = true;
			for (Service s : getServices())
				s.make();
			if (Main.scriptSupport && Main.jsTemplate)
				limax.xmlgen.java.Viewgen.makeJavaScriptTemplate(this.getDescendants(View.class));
			if (Main.luaTemplate && (Main.scriptSupport || Main.variantSupport))
				limax.xmlgen.java.Viewgen.makeLuaTemplate(this.getDescendants(View.class));
			limax.xmlgen.java.Netgen.makeBeans(cbeans, beans);
			Main.isMakingNet = false;
			Main.isMakingZdb = true;
			if (hasServerOrProviderOrNoservice()) {
				Zdb zdb = getZdb();
				if (zdb != null)
					Zdbgen.make(zdb, new File(Main.outputPath, "gen"));
				new limax.xmlgen.java.Monitorgen(getDescendants(Monitorset.class)).make();
			}
			Main.isMakingZdb = false;
		}
	}
}
