package limax.endpoint.variant;

import java.util.Collection;

public interface VariantViewDefinition {
	String getViewName();

	boolean isTemporary();

	Collection<String> getVariableNames();

	Collection<String> getSubscribeNames();

	Collection<String> getControlNames();
}