package limax.endpoint.variant;

import limax.endpoint.ViewChangedType;

public interface VariantViewChangedEvent {
	VariantView getView();

	long getSessionId();

	String getFieldName();

	Variant getValue();

	ViewChangedType getType();
}
