package limax.endpoint.variant;

public interface SupportManageVariant {

	void setTemporaryViewHandler(String name, TemporaryViewHandler handler);

	TemporaryViewHandler getTemporaryViewHandler(String name, boolean returnDeafault);

	Short getViewClassIndex(String name);

	VariantManager getVariantManager();
}
