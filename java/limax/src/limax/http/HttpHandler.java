package limax.http;

public interface HttpHandler extends Handler {
	DataSupplier handle(HttpExchange exchange) throws Exception;
}
