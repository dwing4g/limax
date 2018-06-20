package limax.pkix.tool;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class CRLHttpHandler implements HttpHandler {
	private volatile Map<String, StaticWebData> map;

	CRLHttpHandler(OcspSignerConfig ocspSignerConfig, Supplier<Map<URI, byte[]>> supplier) {
		ocspSignerConfig.getScheduler().scheduleAtFixedRate(() -> {
			map = supplier.get().entrySet().stream().filter(e -> e.getValue() != null).collect(Collectors
					.toMap(e -> e.getKey().getPath(), e -> new StaticWebData(e.getValue(), "application/pkix-crl")));
		}, 0, ocspSignerConfig.getNextUpdateDelay(), TimeUnit.DAYS);
	}

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		StaticWebData data = map.get(exchange.getRequestURI().getPath());
		if (data != null)
			data.transfer(exchange);
		else
			exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, -1);
	}
}
