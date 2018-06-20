package limax.endpoint.variant;

import java.util.Map;
import java.util.Set;

import limax.codec.CodecException;
import limax.defines.VariantDefines;
import limax.endpoint.EndpointManager;
import limax.endpoint.View;
import limax.endpoint.ViewContext;
import limax.endpoint.ViewContext.Type;
import limax.net.SizePolicyException;

public final class VariantManager {

	static View createDynamicViewInstance(int providerId, Object viewDefine, TemporaryViewHandler handler,
			ViewContext vc) {
		final ViewDefine vd = (ViewDefine) viewDefine;
		return vd.isTemporary ? new DynamicTemporaryView(providerId, vd, handler, vc)
				: new DynamicView(providerId, vd, vc);
	}

	static void parseViewDefines(VariantDefines vds, Map<Short, Object> viewdefines, Set<String> tempviewnames) {
		new ViewDefine.VariantDefineParser(vds).parse(viewdefines, tempviewnames);
	}

	private final SupportManageVariant manager;
	private final ViewContext viewContext;

	VariantManager(SupportManageVariant manager, ViewContext viewContext) {
		this.manager = manager;
		this.viewContext = viewContext;
	}

	public void setTemporaryViewHandler(String name, TemporaryViewHandler handler) {
		manager.setTemporaryViewHandler(name, handler);
	}

	public TemporaryViewHandler getTemporaryViewHandler(String name) {
		return manager.getTemporaryViewHandler(name, false);
	}

	public VariantView getSessionOrGlobalView(String name) {
		final Short classindex = manager.getViewClassIndex(name);
		if (null == classindex)
			return null;
		return ((DynamicView) viewContext.getSessionOrGlobalView(classindex)).variantView;
	}

	public VariantView findTemporaryView(String name, int instanceindex) {
		final Short classindex = manager.getViewClassIndex(name);
		if (null == classindex)
			return null;
		return ((DynamicTemporaryView) viewContext.findTemporaryView(classindex, instanceindex)).variantView;
	}

	public void sendMessage(VariantView view, String msg)
			throws InstantiationException, ClassCastException, SizePolicyException, CodecException {
		viewContext.sendMessage(((AbstractVariantView) view).getView(), msg);
	}

	public static VariantManager getInstance(EndpointManager manager, int pvid) {
		ViewContext vc = manager.getViewContext(pvid, Type.Variant);
		return vc == null ? null : ((SupportManageVariant) vc).getVariantManager();
	}

}
