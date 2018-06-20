package limax.provider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import limax.codec.CodecException;
import limax.codec.MD5;
import limax.codec.Octets;
import limax.codec.StringStream;
import limax.defines.VariantDefines;
import limax.net.SizePolicyException;
import limax.provider.providerendpoint.SyncViewToClients;
import limax.util.Enable;
import limax.util.Resource;

abstract class ViewContextImpl implements ViewContext {
	private final Resource resources = Resource.createRoot();
	private final Map<Short, GlobalView> globalViews = new HashMap<>();
	private final List<ViewStub> sessionViewStubs = new ArrayList<>();
	private final Map<Short, ViewStub> temporaryViewStubs = new HashMap<>();
	private final Map<ViewStub, Constructor<? extends View>> constructors = new IdentityHashMap<>();
	private final Set<TemporaryView> temporaryViews = Collections
			.newSetFromMap(Collections.synchronizedMap(new IdentityHashMap<>()));
	private final AtomicInteger viewCounter = new AtomicInteger();
	private final ProviderManagerConfig config;
	private final VariantDefines variantDefines;
	private final Map<String, Integer> dataDictionary;
	private final String scriptViewNames;
	private volatile Thread closingThread;

	ViewContextImpl(ProviderManagerConfig config, String viewManagerClassName) throws Exception {
		this.config = config;
		Class<?> cls = Class.forName(viewManagerClassName);
		Method method = cls.getDeclaredMethod("initialize", ViewContext.class);
		method.setAccessible(true);
		@SuppressWarnings("unchecked")
		Collection<ViewStub> stubs = (Collection<ViewStub>) method.invoke(null, this);

		Map<String, Integer> dataDictionary = new LinkedHashMap<>();
		String scriptViewNames = null;
		try {
			method = cls.getDeclaredMethod("getDataDictionary");
			method.setAccessible(true);
			int i = 0;
			for (String var : ((String) method.invoke(null)).split(","))
				dataDictionary.put(var, i++);
			StringStream sstream = StringStream.create("M");
			stubs.forEach(stub -> {
				sstream.marshal(stub.getClassIndex()).append("L");
				String s[] = stub.getViewClass().getName().split("\\.");
				for (int j = 2; j < s.length; j++)
					sstream.marshal(dataDictionary.get(s[j]));
				sstream.append(":");
			});
			scriptViewNames = sstream.toString(":");
		} catch (Exception e) {
		}
		this.dataDictionary = Collections.unmodifiableMap(dataDictionary);
		this.scriptViewNames = scriptViewNames;

		VariantDefines variantDefines = null;
		try {
			method = cls.getDeclaredMethod("getVariantDefines");
			method.setAccessible(true);
			variantDefines = (VariantDefines) method.invoke(null);
		} catch (Exception e) {
		}
		this.variantDefines = variantDefines;

		Constructor<? extends View> constructor;
		for (ViewStub stub : stubs) {
			method = stub.getViewClass().getSuperclass().getDeclaredMethod("registerTableListener",
					resources.getClass());
			method.setAccessible(true);
			method.invoke(null, resources);
			switch (stub.getLifecycle()) {
			case global:
				constructor = stub.getViewClass().getDeclaredConstructor(GlobalView.CreateParameter.class);
				constructor.setAccessible(true);
				this.globalViews.put(stub.getClassIndex(),
						(GlobalView) onOpened(constructor.newInstance(new GlobalView.CreateParameter() {
							private final Resource resource = Resource.createRoot();

							@Override
							public ViewContext getViewContext() {
								return ViewContextImpl.this;
							}

							@Override
							public ViewStub getViewStub() {
								return stub;
							}

							@Override
							public Resource getResource() {
								return resource;
							}
						})));
				break;
			case session:
				constructor = stub.getViewClass().getDeclaredConstructor(SessionView.CreateParameter.class);
				constructor.setAccessible(true);
				this.sessionViewStubs.add(stub);
				this.constructors.put(stub, constructor);
				break;
			case temporary:
				constructor = stub.getViewClass().getDeclaredConstructor(TemporaryView.CreateParameter.class);
				constructor.setAccessible(true);
				this.temporaryViewStubs.put(stub.getClassIndex(), stub);
				this.constructors.put(stub, constructor);
			}
		}
	}

	abstract void syncViewToClients(SyncViewToClients protocol)
			throws InstantiationException, SizePolicyException, CodecException, ClassCastException;

	abstract void tunnel(long sessionid, URI group, int label, Octets data) throws TunnelException;

	abstract ViewSession getViewSession(long sessionid);

	void close() {
		closingThread = Thread.currentThread();
		temporaryViews.forEach(view -> view.close());
		globalViews.values().forEach(view -> view.close());
		while (viewCounter.get() != 0)
			LockSupport.park(viewCounter);
		resources.close();
	}

	View onOpened(View view) {
		viewCounter.incrementAndGet();
		return view;
	}

	void onClosed(View view) {
		if (viewCounter.decrementAndGet() == 0)
			LockSupport.unpark(closingThread);
	}

	void onSessionLogin(long sessionid, ViewSession session) throws Exception {
		for (ViewStub stub : sessionViewStubs)
			session.add((SessionView) onOpened(constructors.get(stub).newInstance(new SessionView.CreateParameter() {
				private final Resource resource = Resource.createRoot();

				@Override
				public ViewContext getViewContext() {
					return ViewContextImpl.this;
				}

				@Override
				public ViewStub getViewStub() {
					return stub;
				}

				@Override
				public Resource getResource() {
					return resource;
				}

				@Override
				public long getSessionId() {
					return sessionid;
				}
			})));
	}

	View findView(long sessionid, short classindex, int instanceindex) {
		ViewSession vs = getViewSession(sessionid);
		if (vs == null)
			return null;
		if (instanceindex != 0)
			return vs.findTemporaryView(classindex, instanceindex);
		View view = vs.findSessionView(classindex);
		if (view != null)
			return view;
		view = findGlobalView(classindex);
		if (view != null)
			return view;
		return vs.findTemporaryView(classindex, instanceindex);
	}

	@Override
	public GlobalView findGlobalView(short classindex) {
		return globalViews.get(classindex);
	}

	@Override
	public SessionView findSessionView(long sessionid, short classindex) {
		ViewSession session = getViewSession(sessionid);
		return session != null ? session.findSessionView(classindex) : null;
	}

	@Override
	public TemporaryView findTemporaryView(long sessionid, short classindex, int instanceindex) {
		ViewSession session = getViewSession(sessionid);
		return session != null ? session.findTemporaryView(classindex, instanceindex) : null;
	}

	@Override
	public TemporaryView createTemporaryView(short classindex, boolean loose, int partition) {
		try {
			ViewStub stub = temporaryViewStubs.get(classindex);
			TemporaryView view = (TemporaryView) onOpened(
					constructors.get(stub).newInstance(new TemporaryView.CreateParameter() {
						private final Resource resource = Resource.createRoot();

						@Override
						public ViewContext getViewContext() {
							return ViewContextImpl.this;
						}

						@Override
						public ViewStub getViewStub() {
							return stub;
						}

						@Override
						public Resource getResource() {
							return resource;
						}

						@Override
						public boolean isLoose() {
							return loose;
						}

						@Override
						public int getPartition() {
							return partition < 1 ? 1 : partition;
						}
					}));
			temporaryViews.add(view);
			return view;
		} catch (Exception e) {
			throw new RuntimeException("createTemporaryView with classindex = " + classindex, e);
		}
	}

	@Override
	public void closeTemporaryView(TemporaryView view) {
		if (temporaryViews.remove(view))
			view.close();
	}

	@Override
	public int getProviderId() {
		return config.getProviderId();
	}

	boolean isScriptSupported() {
		return scriptViewNames != null;
	}

	boolean isVariantSupported() {
		return variantDefines != null;
	}

	boolean isScriptEnabled() {
		return isScriptSupported() && config.getAllowUseScript() != Enable.False;
	}

	boolean isVariantEnabled() {
		return isVariantSupported() && config.getAllowUseVariant() != Enable.False;
	}

	VariantDefines getVariantDefines() {
		return variantDefines;
	}

	@Override
	public Map<String, Integer> getDataDictionary() {
		return dataDictionary;
	}

	String getScriptDefines() {
		String dict = String.join(",", dataDictionary.keySet());
		String mac = Base64.getEncoder()
				.encodeToString(MD5.digest((dict + scriptViewNames).getBytes(StandardCharsets.UTF_8)));
		return StringStream.create().marshal(dict + "," + mac.substring(0, mac.length() - 2)).append(scriptViewNames)
				.toString();
	}
}
