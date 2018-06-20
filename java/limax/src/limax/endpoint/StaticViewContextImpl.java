package limax.endpoint;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import limax.endpoint.providerendpoint.SyncViewToClients;

final class StaticViewContextImpl extends AbstractViewContext {

	private final ViewContextImpl impl;
	private final Map<Short, Class<? extends View>> viewClasses;

	private View createView(Class<? extends View> cls) {
		try {
			final Constructor<? extends View> constructor = cls.getDeclaredConstructor(ViewContext.class);
			constructor.setAccessible(true);
			return constructor.newInstance(StaticViewContextImpl.this);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	StaticViewContextImpl(final int pvid, final Map<Short, Class<? extends View>> map, EndpointManagerImpl netmanager) {
		viewClasses = new HashMap<Short, Class<? extends View>>(map);
		impl = new ViewContextImpl(new ViewContextImpl.createViewInstance() {
			@Override
			public int getProviderId() {
				return pvid;
			}

			@Override
			public View createView(short clsindex) {
				Class<? extends View> cls = viewClasses.get(clsindex);
				return cls == null ? null : StaticViewContextImpl.this.createView(cls);
			}
		}, netmanager);
		for (Class<? extends View> cls : viewClasses.values())
			createView(cls);
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
	public Type getType() {
		return Type.Static;
	}

	@Override
	void onSyncViewToClients(SyncViewToClients protocol) throws Exception {
		impl.onSyncViewToClients(protocol);
	}

	@Override
	void clear() {
		impl.clear();
	}
}
