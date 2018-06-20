package limax.endpoint;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import limax.defines.VariantDefines;
import limax.endpoint.providerendpoint.SyncViewToClients;
import limax.endpoint.variant.SupportManageVariant;
import limax.endpoint.variant.TemporaryViewHandler;
import limax.endpoint.variant.VariantManager;
import limax.endpoint.variant.VariantView;

final class VariantViewContextImpl extends AbstractViewContext implements SupportManageVariant {

	private final static TemporaryViewHandler hollowTempViewHandler = new TemporaryViewHandler() {

		@Override
		public void onOpen(VariantView view, Collection<Long> sessionids) {
		}

		@Override
		public void onClose(VariantView view) {
		}

		@Override
		public void onAttach(VariantView view, long sessionid) {
		}

		@Override
		public void onDetach(VariantView view, long sessionid, int reason) {
		}

	};

	private final ViewContextImpl impl;
	private final Map<Short, Object> viewdefines;
	private final Set<String> tempviewnames;
	private final Map<String, Short> nametoidmap = new HashMap<String, Short>();
	private final Map<String, TemporaryViewHandler> tempviewhandler = new HashMap<String, TemporaryViewHandler>();
	private final VariantManager variantmanager;
	private final Method viewCreator;

	private VariantViewContextImpl(final int pvid, Map<Short, Object> viewdefines, Set<String> tempviewnames,
			EndpointManagerImpl netmanager) {
		this.impl = new ViewContextImpl(new ViewContextImpl.createViewInstance() {

			@Override
			public int getProviderId() {
				return pvid;
			}

			@Override
			public View createView(short classindex) {
				return createViewInstance(classindex, VariantViewContextImpl.this);
			}
		}, netmanager);
		this.viewdefines = viewdefines;
		this.tempviewnames = tempviewnames;
		for (Map.Entry<Short, Object> e : viewdefines.entrySet())
			nametoidmap.put(e.getValue().toString(), e.getKey());
		try {
			viewCreator = VariantManager.class.getDeclaredMethod("createDynamicViewInstance", int.class, Object.class,
					TemporaryViewHandler.class, ViewContext.class);
			viewCreator.setAccessible(true);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		try {
			Constructor<VariantManager> constructor = VariantManager.class
					.getDeclaredConstructor(SupportManageVariant.class, ViewContext.class);
			constructor.setAccessible(true);
			variantmanager = constructor.newInstance(this, this);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private View createViewInstance(short index, ViewContext vc) {
		final Object vd = viewdefines.get(index);
		if (null == vd)
			return null;
		try {
			return (View) viewCreator.invoke(null, impl.getProviderId(), vd,
					getTemporaryViewHandler(vd.toString(), true), vc);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Short getViewClassIndex(String name) {
		return nametoidmap.get(name);
	}

	@Override
	public synchronized void setTemporaryViewHandler(String name, TemporaryViewHandler handler) {
		if (!tempviewnames.contains(name))
			throw new IllegalArgumentException("temporary view named \"" + name + "\" not exist");
		tempviewhandler.put(name, handler);
	}

	@Override
	public synchronized TemporaryViewHandler getTemporaryViewHandler(String name, boolean returnDeafault) {
		TemporaryViewHandler handler = tempviewhandler.get(name);
		return null == handler ? (returnDeafault ? hollowTempViewHandler : null) : handler;
	}

	@Override
	public VariantManager getVariantManager() {
		return variantmanager;
	}

	@Override
	public Type getType() {
		return Type.Variant;
	}

	@Override
	public View getSessionOrGlobalView(short classindex) {
		return impl.getSesseionOrGlobalView(classindex);
	}

	@Override
	public TemporaryView findTemporaryView(short classindex, int instanceindex) {
		return impl.findTemporaryView(classindex, instanceindex);
	}

	@Override
	public EndpointManager getEndpointManager() {
		return impl.getEndpointManager();
	}

	@Override
	public int getProviderId() {
		return impl.getProviderId();
	}

	@Override
	void onSyncViewToClients(SyncViewToClients protocol) throws Exception {
		impl.onSyncViewToClients(protocol);
	}

	@Override
	void clear() {
		impl.clear();
	}

	static VariantViewContextImpl createInstance(int pvid, VariantDefines defines, EndpointManagerImpl netmanager) {
		final Map<Short, Object> viewdefines = new HashMap<Short, Object>();
		final Set<String> tempviewnames = new HashSet<String>();
		try {
			Method method = VariantManager.class.getDeclaredMethod("parseViewDefines", VariantDefines.class, Map.class,
					Set.class);
			method.setAccessible(true);
			method.invoke(null, defines, viewdefines, tempviewnames);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return new VariantViewContextImpl(pvid, viewdefines, tempviewnames, netmanager);
	}
}
