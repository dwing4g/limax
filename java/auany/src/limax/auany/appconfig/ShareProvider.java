package limax.auany.appconfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import limax.util.ElementHelper;
import limax.util.XMLUtils;

class ShareProvider extends Provider {
	private final List<App> apps = new ArrayList<>();
	private long jsonPublishDelayMin;

	private ShareProvider(Element self, Context ctx) {
		super(new ElementHelper(self).getInt("id"), new ElementHelper(self).getString("key"));
		if (getId() <= 0)
			ctx.updateErrorMessage("Provider id must > 0, but " + getId());
		this.jsonPublishDelayMin = new ElementHelper(self).getLong("jsonPublishDelayMin", 30000l);
	}

	Element createElement(Document doc) {
		Element e = super.createElement(doc);
		e.setAttribute("jsonPublishDelayMin", String.valueOf(jsonPublishDelayMin));
		return e;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ShareProvider))
			return false;
		return super.equals(o) && ((ShareProvider) o).jsonPublishDelayMin == this.jsonPublishDelayMin;
	}

	static ShareProvider get(Context ctx, int pvid) {
		return ctx.shareProviderMap.get(pvid);
	}

	static void load(Element self, Context ctx) {
		List<Integer> conflict = XMLUtils.getChildElements(self).stream().filter(e -> e.getTagName().equals("provider"))
				.map(e -> new ShareProvider(e, ctx)).map(p -> ctx.shareProviderMap.putIfAbsent(p.getId(), p))
				.filter(Objects::nonNull).map(ShareProvider::getId).collect(Collectors.toList());
		if (!conflict.isEmpty())
			ctx.updateErrorMessage("Provider id conflict " + conflict);
	}

	static void visit(Context ctx, int pvid, Consumer<ShareProvider> found, Runnable notfound) {
		ShareProvider provider = ctx.shareProviderMap.get(pvid);
		if (provider == null)
			notfound.run();
		else
			found.accept(provider);
	}

	static <R> R action(Context ctx, int pvid, Function<ShareProvider, R> found, Supplier<R> notfound) {
		ShareProvider provider = ctx.shareProviderMap.get(pvid);
		return provider == null ? notfound.get() : found.apply(provider);
	}

	static List<Integer> getOptionalPvids(Context ctx) {
		return ctx.shareProviderMap.values().stream().filter(p -> p.apps.isEmpty()).map(Provider::getId)
				.collect(Collectors.toList());
	}

	void link(App app) {
		apps.add(app);
	}

	boolean updateJSON(String json) {
		if (super.updateJSON(json))
			apps.forEach(App::flushCache);
		return true;
	}

	boolean updateStatus(Status status) {
		if (super.updateStatus(status))
			apps.forEach(App::flushCache);
		return true;
	}

	long getJSONPublishDelayMin() {
		return jsonPublishDelayMin;
	}

	static List<Element> createElements(Document doc, Context ctx) {
		return ctx.shareProviderMap.values().stream().sorted(Comparator.comparingInt(Provider::getId))
				.map(p -> p.createElement(doc)).collect(Collectors.toList());
	}

}