package limax.auany.appconfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import limax.auany.SessionManager;
import limax.auany.appconfig.Provider.Status;
import limax.auany.json.AppInfo;
import limax.auany.json.ServiceInfo;
import limax.auany.json.SwitcherInfo;
import limax.util.ElementHelper;
import limax.util.XMLUtils;

class App {
	private final int id;
	private final int maxSubordinates;
	private final long jsonPublishDelayMin;
	private final boolean providerMatchBidirectionally;
	private final List<Integer> sharePvids;
	private final Map<ServiceType, List<Switcher>> switchers;
	private final Map<Integer, Service> services = new HashMap<>();

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof App))
			return false;
		App a = (App) o;
		return a.id == this.id && a.maxSubordinates == this.maxSubordinates
				&& a.jsonPublishDelayMin == this.jsonPublishDelayMin
				&& a.providerMatchBidirectionally == this.providerMatchBidirectionally
				&& a.sharePvids.equals(this.sharePvids);
	}

	class Service {
		private final int id;
		private final Map<ServiceType, List<Switcher>> switchers;
		private final List<Provider> providers;
		private final List<Integer> sharePvids;
		private final Set<Integer> pvids;
		private String optional = "";

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Service))
				return false;
			Service s = (Service) o;
			return s.id == this.id && s.switchers.equals(this.switchers) && s.providers.equals(this.providers);
		}

		class PrivateProvider extends Provider {
			private PrivateProvider(Element self, Context ctx) {
				super(new ElementHelper(self).getInt("id"), new ElementHelper(self).getString("key"));
				if (getId() <= 0)
					ctx.updateErrorMessage("App.id = " + getApp().id + " Service.id = " + getService().id
							+ " Provider id must > 0, but " + getId());
				if (ShareProvider.get(ctx, getId()) != null || ctx.privateProviderMap.put(getId(), this) != null)
					ctx.updateErrorMessage("App.id = " + getApp().id + " Service.id = " + getService().id
							+ " conflict Provider id = " + getId());
			}

			@Override
			public boolean equals(Object o) {
				return o instanceof PrivateProvider ? super.equals(o) : false;
			}

			@Override
			boolean updateJSON(String json) {
				if (super.updateJSON(json))
					getApp().flushCache();
				return true;
			}

			void updateSwitcher(Status status, Set<Integer> switcherIds) {
				Service.this.switchers.values().stream().flatMap(switchers -> switchers.stream())
						.filter(switcher -> switcherIds.contains(switcher.getId()))
						.forEach(switcher -> switcher.updateProvider(this, status));
				if (status != Status.OFF) {
					super.updateStatus(status);
				} else if (Service.this.switchers.values().stream().flatMap(switchers -> switchers.stream())
						.noneMatch(switcher -> switcher.isProviderActive(this))) {
					super.updateStatus(Status.OFF);
				}
				getApp().flushCache();
			}

			Service getService() {
				return Service.this;
			}

			App getApp() {
				return App.this;
			}
		}

		private Service(Element self, Context ctx) {
			ElementHelper eh = new ElementHelper(self);
			this.id = eh.getInt("id");
			this.switchers = Arrays.stream(eh.getString("switcher").split(",")).map(String::trim)
					.filter(s -> !s.isEmpty()).map(s -> Objects.requireNonNull(Switcher.get(ctx, Integer.parseInt(s))))
					.collect(Collectors.groupingBy(Switcher::getType));
			this.providers = XMLUtils.getChildElements(self).stream().filter(e -> e.getTagName().equals("provider"))
					.map(e -> new PrivateProvider(e, ctx)).collect(Collectors.toList());
			if (this.switchers.isEmpty())
				ctx.updateErrorMessage("App.id = " + getApp().id + " Service.id = " + id
						+ " service must have at least one switcher.");
			if (this.providers.isEmpty())
				ctx.updateErrorMessage("App.id = " + getApp().id + " Service.id = " + id
						+ " service must have at least one provider.");
			this.pvids = providers.stream().map(Provider::getId).collect(Collectors.toSet());
			List<Provider> shareProviders = Arrays.stream(eh.getString("shareProvider").split(",")).map(String::trim)
					.filter(s -> !s.isEmpty()).map(Integer::parseInt).map(pvid -> ShareProvider.get(ctx, pvid))
					.peek(cp -> cp.link(App.this)).collect(Collectors.toList());
			this.sharePvids = shareProviders.stream().map(Provider::getId).collect(Collectors.toList());
			if (sharePvids.contains(SessionManager.providerId))
				ctx.updateErrorMessage("app.id = " + id + " service.id = " + id + " CANNOT reference ProviderId = "
						+ SessionManager.providerId);
			this.providers.addAll(shareProviders);
			this.pvids.addAll(this.sharePvids);
		}

		private App getApp() {
			return App.this;
		}

		int getId() {
			return id;
		}

		private Element createElement(Document doc) {
			Element e = doc.createElement("service");
			e.setAttribute("id", String.valueOf(id));
			e.setAttribute("shareProvider",
					sharePvids.stream().sorted().map(String::valueOf).collect(Collectors.joining(",")));
			e.setAttribute("switcher", switchers.values().stream().flatMap(List::stream).map(Switcher::getId).sorted()
					.map(String::valueOf).collect(Collectors.joining(",")));
			providers.stream().filter(p -> p instanceof PrivateProvider).map(p -> (PrivateProvider) p)
					.sorted(Comparator.comparingInt(Provider::getId)).forEach(p -> e.appendChild(p.createElement(doc)));
			return e;
		}

		private ServiceInfo createInfo(ServiceType type) {
			List<Integer> pvids = new ArrayList<>();
			List<Integer> payids = new ArrayList<>();
			List<String> userjsons = new ArrayList<>();
			boolean running = providers.stream().map(provider -> {
				pvids.add(provider.getId());
				if (!provider.getJSON().isEmpty())
					userjsons.add(provider.getJSON());
				if (provider.getStatus() == Status.ON_PAY)
					payids.add(provider.getId());
				return provider.getStatus() != Status.OFF;
			}).reduce(true, Boolean::logicalAnd);
			List<SwitcherInfo> switcherInfos = switchers.get(type).stream().filter(Switcher::isRunning)
					.filter(switcher -> switcher.isProviderActive(providers)).map(Switcher::getInfo)
					.collect(Collectors.toList());
			return new ServiceInfo(switcherInfos, pvids, payids, userjsons, running, optional);
		}
	}

	private App(Element self, Context ctx) {
		ElementHelper eh = new ElementHelper(self);
		this.id = eh.getInt("id");
		this.maxSubordinates = eh.getInt("maxSubordinates", 0);
		this.jsonPublishDelayMin = eh.getLong("jsonPublishDelayMin", 30000l);
		this.providerMatchBidirectionally = eh.getBoolean("providerMatchBidirectionally", true);
		List<Integer> conflict = XMLUtils.getChildElements(self).stream().filter(e -> e.getTagName().equals("service"))
				.map(e -> new Service(e, ctx)).map(s -> services.putIfAbsent(s.id, s)).filter(Objects::nonNull)
				.map(Service::getId).collect(Collectors.toList());
		if (!conflict.isEmpty())
			ctx.updateErrorMessage("Service id conflict + " + conflict + " app.id = " + id);
		this.switchers = this.services.values().stream().flatMap(s -> s.switchers.values().stream())
				.flatMap(e -> e.stream()).peek(s -> s.getApps().add(this))
				.collect(Collectors.groupingBy(Switcher::getType));
		List<Provider> shareProviders = Arrays.stream(eh.getString("shareProvider").split(",")).map(String::trim)
				.filter(s -> !s.isEmpty()).map(Integer::parseInt).map(pvid -> ShareProvider.get(ctx, pvid))
				.peek(cp -> cp.link(this)).collect(Collectors.toList());
		this.sharePvids = shareProviders.stream().map(Provider::getId).collect(Collectors.toList());
		if (sharePvids.contains(SessionManager.providerId))
			ctx.updateErrorMessage("app.id = " + id + " CANNOT reference ProviderId = " + SessionManager.providerId);
		services.values().forEach(service -> {
			service.providers.addAll(shareProviders);
			service.pvids.addAll(sharePvids);
		});
	}

	Map<Integer, Service> getServices() {
		return services;
	}

	int getId() {
		return id;
	}

	private Element createElement(Document doc) {
		Element e = doc.createElement("app");
		ElementHelper eh = new ElementHelper(e);
		eh.set("id", id);
		eh.set("maxSubordinates", maxSubordinates);
		eh.set("jsonPublishDelayMin", jsonPublishDelayMin);
		eh.set("shareProvider", sharePvids.stream().sorted().map(String::valueOf).collect(Collectors.joining(",")));
		services.values().stream().sorted(Comparator.comparingInt(s -> s.id))
				.forEach(s -> e.appendChild(s.createElement(doc)));
		eh.set("providerMatchBidirectionally", providerMatchBidirectionally);
		return e;
	}

	void flushCache() {
		AppManager.flushCache(id);
	}

	AppInfo createInfo(ServiceType type) {
		return new AppInfo(services.values().stream().map(service -> service.createInfo(type)).filter(Objects::nonNull)
				.collect(Collectors.toList()));
	}

	static Context load(Element self, Context current) {
		Context ctx = new Context();
		Switcher.load(self, ctx);
		ctx.mergeSwitcher(current);
		ShareProvider.load(self, ctx);
		ctx.mergeShareProvider(current);
		List<Integer> conflict = XMLUtils.getChildElements(self).stream().filter(e -> e.getTagName().equals("app"))
				.map(e -> new App(e, ctx)).map(a -> ctx.appMap.putIfAbsent(a.id, a)).filter(Objects::nonNull)
				.map(App::getId).collect(Collectors.toList());
		if (!conflict.isEmpty())
			ctx.updateErrorMessage("App id conflict " + conflict);
		ctx.mergeApp(current);
		ctx.initOptionalPvids();
		return ctx;
	}

	static int getMaxSubordinates(Context ctx, int appid) {
		return ctx.appMap.get(appid).maxSubordinates;
	}

	static boolean verifyProviderKey(Context ctx, int pvid, String key) {
		return ShareProvider.action(ctx, pvid, provider -> provider.verifyKey(key),
				() -> ctx.privateProviderMap.get(pvid).verifyKey(key));
	}

	static void updateJSON(Context ctx, int pvid, String json) {
		ShareProvider.action(ctx, pvid, provider -> provider.updateJSON(json),
				() -> ctx.privateProviderMap.get(pvid).updateJSON(json));
	}

	static long initProvider(Context ctx, int pvid, String json) {
		return ShareProvider.action(ctx, pvid, provider -> {
			provider.updateJSON(json);
			return provider.getJSONPublishDelayMin();
		}, () -> {
			App.Service.PrivateProvider p = ctx.privateProviderMap.get(pvid);
			p.updateJSON(json);
			return p.getApp().jsonPublishDelayMin;
		});
	}

	static void updateProvider(Context ctx, int pvid, Provider.Status status, Set<Integer> switcherIds) {
		ShareProvider.visit(ctx, pvid, p -> p.updateStatus(status), () -> {
			App.Service.PrivateProvider p = ctx.privateProviderMap.get(pvid);
			if (p != null)
				p.updateSwitcher(status, switcherIds);
		});
	}

	static SwitcherInfo randomSwitcher(Context ctx, ServiceType type, int appid) {
		SwitcherInfo[] list = ctx.appMap.get(appid).switchers.get(type).stream().filter(Switcher::isRunning)
				.map(Switcher::getInfo).toArray(SwitcherInfo[]::new);
		return list[ThreadLocalRandom.current().nextInt(list.length)];
	}

	private static void updateSwitcher(Context ctx, String host, String key, List<Integer> ids, ServiceType type,
			StringBuilder message, List<Runnable> log, Set<App> infectapps) {
		ids.forEach(id -> {
			Switcher s = Switcher.get(ctx, id);
			if (s != null) {
				if (s.verifyKey(key))
					if (s.getType() == type) {
						String prevhost = s.update(host, log, infectapps);
						if (!prevhost.isEmpty())
							message.append("Switcher config error --- switcher id = " + id + " already running on "
									+ prevhost + "\n");
					} else
						message.append("Switcher config error --- switcher id = " + id + " type = " + type
								+ " but in auany type = " + s.getType() + "\n");
				else
					message.append("Switcher config error --- switcher id = " + id + " announce wrong key\n");
			} else {
				message.append("Switcher config error --- switcher id = " + id + " not config in auany\n");
			}
		});
	}

	static String updateSwitcher(Context ctx, String host, String key, List<Integer> nativeIds, List<Integer> wsIds,
			List<Integer> wssIds) {
		if (nativeIds.size() + wsIds.size() + wssIds.size() == 0)
			return "Switcher config error --- not config any switcher id\n";
		StringBuilder message = new StringBuilder();
		List<Runnable> log = new ArrayList<>();
		Set<App> infectapps = new HashSet<>();
		updateSwitcher(ctx, host, key, nativeIds, ServiceType.NATIVE, message, log, infectapps);
		updateSwitcher(ctx, host, key, wsIds, ServiceType.WS, message, log, infectapps);
		updateSwitcher(ctx, host, key, wssIds, ServiceType.WSS, message, log, infectapps);
		if (message.length() == 0) {
			log.forEach(Runnable::run);
			infectapps.forEach(App::flushCache);
		}
		return message.toString();
	}

	static Integer checkAppId(Context ctx, Collection<Integer> pvids) {
		Set<Integer> set = pvids.stream().filter(pvid -> !ctx.optionalPvids.contains(pvid)).collect(Collectors.toSet());
		if (set.isEmpty())
			return null;
		App.Service.PrivateProvider provider = ctx.privateProviderMap.get(set.iterator().next());
		Set<Integer> servicePvids = provider.getService().pvids;
		return servicePvids.containsAll(set)
				&& (!provider.getApp().providerMatchBidirectionally || set.size() == servicePvids.size())
						? provider.getApp().id : null;
	}

	static void setServiceOptional(Context ctx, int appid, int serviceid, String optional) {
		App app = ctx.appMap.get(appid);
		app.services.get(serviceid).optional = optional;
		app.flushCache();
	}

	static List<Element> createElements(Document doc, Context ctx) {
		return ctx.appMap.values().stream().sorted(Comparator.comparingInt(a -> a.id)).map(a -> a.createElement(doc))
				.collect(Collectors.toList());
	}
}