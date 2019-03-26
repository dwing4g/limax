package limax.pkix.tool;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import limax.http.DataSupplier;
import limax.http.HttpExchange;
import limax.http.HttpHandler;

class CRLHttpHandler implements HttpHandler {
	private volatile Map<String, StaticWebData> map;

	CRLHttpHandler(OcspSignerConfig ocspSignerConfig, Supplier<Map<URI, byte[]>> supplier) {
		ocspSignerConfig.getScheduler().scheduleAtFixedRate(() -> {
			map = supplier.get().entrySet().stream().filter(e -> e.getValue() != null).collect(Collectors
					.toMap(e -> e.getKey().getPath(), e -> new StaticWebData(e.getValue(), "application/pkix-crl")));
		}, 0, ocspSignerConfig.getNextUpdateDelay(), TimeUnit.DAYS);
	}

	@Override
	public DataSupplier handle(HttpExchange exchange) throws Exception {
		StaticWebData data = map.get(exchange.getRequestURI().getPath());
		if (data != null)
			return data.handle(exchange);
		exchange.getResponseHeaders().set(":status", HttpURLConnection.HTTP_NOT_FOUND);
		return null;
	}
}
