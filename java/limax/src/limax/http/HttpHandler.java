package limax.http;

@FunctionalInterface
public interface HttpHandler extends Handler {
	DataSupplier handle(HttpExchange exchange) throws Exception;

	default void censor(HttpExchange exchange) throws Exception {
		throw new UnsupportedOperationException();
	}
}
