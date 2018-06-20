package limax.endpoint;

public interface ViewChangedEvent {
	View getView();

	long getSessionId();

	String getFieldName();

	Object getValue();

	ViewChangedType getType();
}