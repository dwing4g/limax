package limax.http;

@FunctionalInterface
public interface HttpHandler extends Handler {
	DataSupplier handle(HttpExchange exchange) throws Exception;

	default long postLimit() {
		return 0L;
	}
}
