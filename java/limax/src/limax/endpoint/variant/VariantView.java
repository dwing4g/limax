package limax.endpoint.variant;

import limax.codec.CodecException;
import limax.endpoint.ViewVisitor;
import limax.net.SizePolicyException;

public interface VariantView {
	String getViewName();

	void visitField(String fieldname, ViewVisitor<Variant> visitor);

	void sendControl(String controlname, Variant arg)
			throws InstantiationException, SizePolicyException, CodecException;

	void sendMessage(String msg) throws InstantiationException, ClassCastException, SizePolicyException, CodecException;

	Runnable registerListener(VariantViewChangedListener listener);

	Runnable registerListener(String fieldname, VariantViewChangedListener listener);

	int getInstanceIndex();

	boolean isTemporaryView();

	VariantViewDefinition getDefinition();
}
