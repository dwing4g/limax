package limax.http;

@FunctionalInterface
public interface WebSocketHandler extends Handler {
	void handle(WebSocketEvent event) throws Exception;
}
