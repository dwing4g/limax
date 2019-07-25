package limax.auany.paygws;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.w3c.dom.Element;

import limax.auany.PayGateway;
import limax.auany.PayOrder;
import limax.defines.ErrorCodes;
import limax.defines.ErrorSource;
import limax.endpoint.AuanyService.Result;
import limax.http.DataSupplier;
import limax.http.Headers;
import limax.http.HttpExchange;
import limax.http.HttpHandler;
import limax.net.Engine;
import limax.util.ElementHelper;
import limax.util.Trace;

public final class Simulation implements PayGateway, HttpHandler {
	private final static byte[] OK = "OK".getBytes(StandardCharsets.UTF_8);
	private final static byte[] FAIL = "FAIL".getBytes(StandardCharsets.UTF_8);
	private int gateway;
	private String httpContext;
	private long maxDeliveryRandomDelay = 0l;
	private int maxAmount = 0;;

	@Override
	public void initialize(Element e, BiConsumer<String, HttpHandler> httphandlers) {
		ElementHelper eh = new ElementHelper(e);
		gateway = eh.getInt("gateway");
		httpContext = eh.getString("httpContext");
		maxAmount = eh.getInt("maxAmount", maxAmount);
		maxDeliveryRandomDelay = eh.getLong("maxDeliveryRandomDelay", maxDeliveryRandomDelay);
		httphandlers.accept(httpContext, this);
	}

	@Override
	public void unInitialize() {
	}

	private DataSupplier response(HttpExchange exchange, boolean succeed) {
		Headers responseHeaders = exchange.getResponseHeaders();
		responseHeaders.set("Content-Type", "text/plain; charset=utf-8");
		return DataSupplier.from(succeed ? OK : FAIL);
	}

	@Override
	public DataSupplier handle(HttpExchange exchange) {
		URI uri = exchange.getRequestURI();
		if (!uri.getPath().toString().equals(httpContext)) {
			return response(exchange, false);
		}
		try {
			PayOrder.ok(Long.parseLong(URLDecoder.decode(uri.getQuery(), "UTF-8"), Character.MAX_RADIX), gateway);
			return response(exchange, true);
		} catch (Exception e) {
			return response(exchange, false);
		}
	}

	@Override
	public void onPay(long sessionid, int gateway, int payid, int product, int price, int quantity, String receipt,
			Result onresult) throws Exception {
		long serial = PayOrder.create(sessionid, gateway, payid, product, price, quantity);
		onresult.apply(ErrorSource.LIMAX, ErrorCodes.SUCCEED, Long.toString(serial, Character.MAX_RADIX));
		AtomicReference<Future<?>> af = new AtomicReference<>();
		af.set(Engine.getProtocolScheduler().scheduleWithFixedDelay(() -> {
			af.get().cancel(false);
			try {
				PayOrder o = PayOrder.get(serial);
				if (o == null) {
					if (Trace.isErrorEnabled())
						Trace.error("Simulation.proxy miss order = " + Long.toString(serial, Character.MAX_RADIX));
					return;
				}
				if (o.getSessionId() == sessionid && o.getPayId() == payid && o.getProduct() == o.getProduct()
						&& o.getPrice() == price && o.getQuantity() == quantity) {
					int amount = price * quantity;
					if (amount > 0 && amount < maxAmount)
						PayOrder.ok(serial, gateway);
					else
						PayOrder.fail(serial, gateway, "amount = " + amount + " but maxAmount = " + maxAmount);
				} else
					PayOrder.fail(serial, gateway, "check error");
			} catch (Exception e) {
				if (Trace.isErrorEnabled())
					Trace.error("Simulation.proxy", e);
			}
		}, (ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE) % maxDeliveryRandomDelay, Long.MAX_VALUE,
				TimeUnit.MILLISECONDS));
	}

}
