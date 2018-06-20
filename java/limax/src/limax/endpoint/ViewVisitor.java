package limax.endpoint;

public interface ViewVisitor<T> {
	void accept(T value);
}
