package limax.endpoint.variant;

public interface Declaration {
	VariantType getType();

	MarshalMethod createMarshalMethod();
}
